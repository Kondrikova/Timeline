package project.timeline.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Плановый объём работ одной роли по одной задаче в одном спринте, в SP.
 *
 * <p>Ссылки на задачу, спринт и дисциплину — идентификаторы без внешних ключей:
 * это чужие контексты. Целостность через границу сервиса обеспечивают саги
 * удаления, а не СУБД.
 */
@Entity
@Table(name = "allocation")
public class Allocation {

	@Id
	private UUID id;

	@Column(name = "plan_version_id", nullable = false)
	private UUID planVersionId;

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "sprint_id", nullable = false)
	private UUID sprintId;

	@Column(name = "discipline_id", nullable = false)
	private UUID disciplineId;

	@Column(name = "planned_sp", nullable = false)
	private BigDecimal plannedSp;

	@Version
	private Long version;

	protected Allocation() {
	}

	public Allocation(UUID planVersionId, UUID taskId, UUID sprintId, UUID disciplineId, BigDecimal plannedSp) {
		this.id = UUID.randomUUID();
		this.planVersionId = planVersionId;
		this.taskId = taskId;
		this.sprintId = sprintId;
		this.disciplineId = disciplineId;
		changeAmount(plannedSp);
	}

	public final void changeAmount(BigDecimal plannedSp) {
		if (plannedSp == null || plannedSp.signum() < 0) {
			throw new IllegalArgumentException("Плановый объём в SP не может быть отрицательным");
		}
		this.plannedSp = plannedSp;
	}

	public void moveTo(UUID sprintId) {
		this.sprintId = sprintId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPlanVersionId() {
		return planVersionId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public BigDecimal getPlannedSp() {
		return plannedSp;
	}
}
