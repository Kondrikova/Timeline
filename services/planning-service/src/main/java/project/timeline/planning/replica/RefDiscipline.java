package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Реплика дисциплины. Velocity нужна, чтобы перевести человеко-дни в SP. */
@Entity
@Table(name = "ref_discipline")
public class RefDiscipline {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "velocity_sp_per_sprint", nullable = false)
	private BigDecimal velocitySpPerSprint;

	protected RefDiscipline() {
	}

	public RefDiscipline(UUID id, String code, String name, BigDecimal velocitySpPerSprint) {
		this.id = id;
		update(code, name, velocitySpPerSprint);
	}

	public final void update(String code, String name, BigDecimal velocitySpPerSprint) {
		this.code = code;
		this.name = name;
		this.velocitySpPerSprint = velocitySpPerSprint;
	}

	public BigDecimal velocitySpPerDay(int referenceWorkingDays) {
		return velocitySpPerSprint.divide(BigDecimal.valueOf(referenceWorkingDays), 6, RoundingMode.HALF_UP);
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getVelocitySpPerSprint() {
		return velocitySpPerSprint;
	}
}
