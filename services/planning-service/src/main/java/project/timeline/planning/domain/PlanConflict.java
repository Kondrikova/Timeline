package project.timeline.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Обнаруженное нарушение плана.
 *
 * <p>Конфликты материализуются, а не вычисляются на каждый запрос: иначе сводку
 * «столько-то предупреждений в плане» нельзя было бы показать без обхода всего
 * графа.
 */
@Entity
@Table(name = "plan_conflict")
public class PlanConflict {

	@Id
	private UUID id;

	@Column(name = "plan_version_id", nullable = false)
	private UUID planVersionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConflictType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConflictType.Severity severity;

	@Column(name = "sprint_id")
	private UUID sprintId;

	@Column(name = "discipline_id")
	private UUID disciplineId;

	@Column(name = "task_id")
	private UUID taskId;

	@Column(nullable = false)
	private String details;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	protected PlanConflict() {
	}

	public PlanConflict(UUID planVersionId, ConflictType type, ConflictType.Severity severity,
			UUID sprintId, UUID disciplineId, UUID taskId, String details) {
		this.id = UUID.randomUUID();
		this.planVersionId = planVersionId;
		this.type = type;
		this.severity = severity;
		this.sprintId = sprintId;
		this.disciplineId = disciplineId;
		this.taskId = taskId;
		this.details = details;
		this.detectedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ConflictType getType() {
		return type;
	}

	public ConflictType.Severity getSeverity() {
		return severity;
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public String getDetails() {
		return details;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}
}
