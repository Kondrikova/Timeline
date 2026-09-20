package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Материализованный результат расчёта ёмкости.
 *
 * <p>Это кэш, а не источник истины: значение в любой момент восстанавливается
 * пересчётом из реплик, поэтому расхождение лечится перечитыванием, а не
 * ручными правками.
 */
@Entity
@Table(name = "sprint_capacity")
@IdClass(SprintCapacity.Key.class)
public class SprintCapacity {

	@Id
	@Column(name = "sprint_id")
	private UUID sprintId;

	@Id
	@Column(name = "discipline_id")
	private UUID disciplineId;

	@Column(name = "available_person_days", nullable = false)
	private int availablePersonDays;

	@Column(name = "vacation_person_days", nullable = false)
	private int vacationPersonDays;

	@Column(name = "capacity_sp", nullable = false)
	private BigDecimal capacitySp;

	@Column(name = "calculated_at", nullable = false)
	private Instant calculatedAt;

	protected SprintCapacity() {
	}

	public SprintCapacity(UUID sprintId, UUID disciplineId, int availablePersonDays,
			int vacationPersonDays, BigDecimal capacitySp) {
		this.sprintId = sprintId;
		this.disciplineId = disciplineId;
		this.availablePersonDays = availablePersonDays;
		this.vacationPersonDays = vacationPersonDays;
		this.capacitySp = capacitySp;
		this.calculatedAt = Instant.now();
	}

	public UUID getSprintId() {
		return sprintId;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public int getAvailablePersonDays() {
		return availablePersonDays;
	}

	public int getVacationPersonDays() {
		return vacationPersonDays;
	}

	public BigDecimal getCapacitySp() {
		return capacitySp;
	}

	public record Key(UUID sprintId, UUID disciplineId) implements Serializable {
	}
}
