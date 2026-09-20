package project.timeline.common.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Переносит накопленные события из таблицы в Kafka.
 *
 * <p>Гарантия доставки — at-least-once: запись помечается опубликованной после
 * подтверждения брокером, поэтому при падении между отправкой и коммитом событие
 * уйдёт повторно. Идемпотентность обеспечивается на стороне потребителя.
 */
@Component
@ConditionalOnProperty(name = "timeline.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

	private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
	private static final int BATCH_SIZE = 100;

	private final OutboxRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	public OutboxPoller(OutboxRepository repository,
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry) {
		this.repository = repository;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;

		Gauge.builder("outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
				.description("Число неопубликованных записей outbox")
				.register(meterRegistry);
		Gauge.builder("outbox.oldest.pending.age.seconds", this, OutboxPoller::oldestPendingAgeSeconds)
				.description("Возраст самой старой неопубликованной записи outbox")
				.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${timeline.outbox.poll-interval:500}")
	@Transactional
	public void publishPending() {
		List<OutboxRecord> batch = repository.lockUnpublished(BATCH_SIZE);
		for (OutboxRecord record : batch) {
			send(record);
			record.markPublished();
		}
		if (!batch.isEmpty()) {
			log.debug("Опубликовано событий: {}", batch.size());
		}
	}

	private void send(OutboxRecord record) {
		ProducerRecord<String, String> message = new ProducerRecord<>(
				record.getTopic(),
				record.getAggregateId().toString(),
				record.getPayload());
		readHeaders(record).forEach((name, value) ->
				message.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
		kafkaTemplate.send(message).join();
	}

	private Map<String, String> readHeaders(OutboxRecord record) {
		if (record.getHeaders() == null) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(record.getHeaders(), new TypeReference<>() {
			});
		}
		catch (Exception e) {
			log.warn("Не удалось прочитать заголовки записи outbox {}", record.getId(), e);
			return Map.of();
		}
	}

	private double oldestPendingAgeSeconds() {
		return repository.oldestUnpublishedCreatedAt()
				.map(created -> (double) Duration.between(created, Instant.now()).toSeconds())
				.orElse(0.0);
	}
}
