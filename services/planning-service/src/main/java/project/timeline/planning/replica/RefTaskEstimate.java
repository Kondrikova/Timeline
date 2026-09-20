package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Реплика оценки. Пустое значение означает «роль задействована, оценки нет». */
@Entity
@Table(name = "ref_task_estimate")
public class RefTaskEstimate {

	@Id
	private UUID id;

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "discipline_id", nullable = false)
	private UUID disciplineId;

	@Column(name = "estimate_sp")
	private BigDecimal estimateSp;

	protected RefTaskEstimate() {
	}

	public RefTaskEstimate(UUID taskId, UUID disciplineId, BigDecimal estimateSp) {
		this.id = UUID.randomUUID();
		this.taskId = taskId;
		this.disciplineId = disciplineId;
		this.estimateSp = estimateSp;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public BigDecimal getEstimateSp() {
		return estimateSp;
	}
}
