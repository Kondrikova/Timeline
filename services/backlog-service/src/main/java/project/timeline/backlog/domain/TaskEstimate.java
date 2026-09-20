package project.timeline.backlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Оценка задачи для одной дисциплины, в SP.
 *
 * <p>Сущность, а не элемент коллекции, именно из-за возможности пустой оценки:
 * Hibernate не сохраняет в element collection записи с {@code null}-значением, и
 * состояние «роль задействована, оценка ещё не дана» было бы потеряно при
 * сохранении.
 */
@Entity
@Table(name = "task_estimate")
public class TaskEstimate {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private Task task;

	@Column(name = "discipline_id", nullable = false)
	private UUID disciplineId;

	/** {@code null} означает «роль участвует, оценка не проставлена». */
	@Column(name = "estimate_sp")
	private BigDecimal estimateSp;

	protected TaskEstimate() {
	}

	TaskEstimate(Task task, UUID disciplineId, BigDecimal estimateSp) {
		if (estimateSp != null && estimateSp.signum() < 0) {
			throw new IllegalArgumentException("Оценка в SP не может быть отрицательной");
		}
		this.id = UUID.randomUUID();
		this.task = task;
		this.disciplineId = disciplineId;
		this.estimateSp = estimateSp;
	}

	void changeEstimateSp(BigDecimal estimateSp) {
		if (estimateSp != null && estimateSp.signum() < 0) {
			throw new IllegalArgumentException("Оценка в SP не может быть отрицательной");
		}
		this.estimateSp = estimateSp;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public BigDecimal getEstimateSp() {
		return estimateSp;
	}

	public Task getTask() {
		return task;
	}
}
