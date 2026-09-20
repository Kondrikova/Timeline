package project.timeline.backlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Состояние распределённой транзакции удаления задачи.
 *
 * <p>Хранится в БД, а не в памяти: иначе перезапуск сервиса между шагами оставил
 * бы задачу в состоянии {@code DELETING} навсегда.
 */
@Entity
@Table(name = "saga_instance")
public class SagaInstance {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SagaState state;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SagaInstance() {
	}

	public SagaInstance(String type, String payload) {
		this.id = UUID.randomUUID();
		this.type = type;
		this.payload = payload;
		this.state = SagaState.STARTED;
		this.startedAt = Instant.now();
		this.updatedAt = this.startedAt;
	}

	public void complete() {
		this.state = SagaState.DONE;
		this.updatedAt = Instant.now();
	}

	public void compensate(String reason) {
		this.state = SagaState.FAILED;
		this.failureReason = reason;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public SagaState getState() {
		return state;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getStartedAt() {
		return startedAt;
	}
}
