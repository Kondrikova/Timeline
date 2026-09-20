package project.timeline.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Счётчик и лог для сообщений, ушедших в DLT после исчерпания retry.
 *
 * <p>Вызывается из {@code @DltHandler} на классе с {@code @RetryableTopic}:
 * обработчик DLT обязан жить рядом с listener'ом.
 */
@Component
public class DltMetrics {

	private static final Logger log = LoggerFactory.getLogger(DltMetrics.class);

	private final Counter dltCounter;

	public DltMetrics(MeterRegistry meterRegistry) {
		this.dltCounter = Counter.builder("kafka.dlt.messages")
				.description("Сообщения, ушедшие в dead-letter topic")
				.register(meterRegistry);
	}

	public void record(String topic, String payload) {
		dltCounter.increment();
		log.error("Сообщение ушло в DLT топика {}: {}", topic, payload);
	}
}
