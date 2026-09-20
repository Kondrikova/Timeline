package project.timeline.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись исходящего события.
 *
 * <p>Вставляется в той же транзакции, что и бизнес-изменение: это исключает
 * расхождение «данные записаны, событие не отправлено» и обратное.
 *
 * <p>В БД таблица секционирована по {@code created_at}, поэтому её первичный ключ
 * составной — {@code (id, created_at)}. Для JPA достаточно {@code id}: он выдаётся
 * последовательностью и уникален глобально.
 */
@Entity
@Table(name = "outbox")
public class OutboxRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(nullable = false)
	private String topic;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@JdbcTypeCode(SqlTypes.JSON)
	private String headers;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxRecord() {
	}

	OutboxRecord(String aggregateType, UUID aggregateId, String eventType, String topic,
			String payload, String headers) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.topic = topic;
		this.payload = payload;
		this.headers = headers;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getTopic() {
		return topic;
	}

	public String getPayload() {
		return payload;
	}

	public String getHeaders() {
		return headers;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	void markPublished() {
		this.publishedAt = Instant.now();
	}
}
