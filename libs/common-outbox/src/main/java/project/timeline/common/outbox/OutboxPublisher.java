package project.timeline.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.DomainEvent;

import java.util.Map;

/**
 * Единственный способ опубликовать доменное событие из бизнес-кода.
 *
 * <p>Метод обязан вызываться внутри уже открытой транзакции: {@link Propagation#MANDATORY}
 * превращает нарушение этого правила в ошибку на месте, а не в потерянное событие.
 */
@Component
public class OutboxPublisher {

	private final OutboxRepository repository;
	private final ObjectMapper objectMapper;

	public OutboxPublisher(OutboxRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(String topic, DomainEvent<?> event) {
		try {
			String payload = objectMapper.writeValueAsString(event);
			String headers = objectMapper.writeValueAsString(Map.of(
					"traceId", event.traceId() == null ? "" : event.traceId(),
					"actorId", event.actorId() == null ? "" : event.actorId()));
			repository.save(new OutboxRecord(
					event.aggregateType(),
					event.aggregateId(),
					event.eventType(),
					topic,
					payload,
					headers));
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Не удалось сериализовать событие " + event.eventType(), e);
		}
	}
}
