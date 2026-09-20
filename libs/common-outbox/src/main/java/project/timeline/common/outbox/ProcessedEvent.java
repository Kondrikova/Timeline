package project.timeline.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Отметка о том, что событие уже обработано конкретным потребителем.
 *
 * <p>Доставка из Kafka выполняется как at-least-once, поэтому повтор — штатная
 * ситуация, а не сбой. Составной первичный ключ переносит защиту от повторной
 * обработки на уровень БД.
 */
@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEvent.Key.class)
public class ProcessedEvent {

	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Id
	@Column(name = "consumer")
	private String consumer;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEvent() {
	}

	public ProcessedEvent(UUID eventId, String consumer) {
		this.eventId = eventId;
		this.consumer = consumer;
		this.processedAt = Instant.now();
	}

	public record Key(UUID eventId, String consumer) implements Serializable {

		public Key {
			Objects.requireNonNull(eventId);
			Objects.requireNonNull(consumer);
		}
	}
}
