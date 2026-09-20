package project.timeline.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Профессиональная роль: backend, frontend, QA, аналитик.
 *
 * <p>Не путать с ролью в приложении ({@code ADMIN}, {@code MEMBER}) — та живёт в
 * Keycloak и к планированию отношения не имеет.
 *
 * <p>{@code velocitySpPerSprint} — сколько SP один сотрудник этой роли закрывает за
 * эталонный спринт. Величина нужна, чтобы перевести доступность, измеряемую в днях,
 * в ёмкость, измеряемую в SP.
 */
@Entity
@Table(name = "discipline")
public class Discipline {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "velocity_sp_per_sprint", nullable = false)
	private BigDecimal velocitySpPerSprint;

	protected Discipline() {
	}

	public Discipline(String code, String name, BigDecimal velocitySpPerSprint) {
		this.id = UUID.randomUUID();
		this.code = code;
		this.name = name;
		setVelocitySpPerSprint(velocitySpPerSprint);
	}

	/**
	 * @param referenceWorkingDays длина эталонного спринта в рабочих днях
	 * @return производительность в SP на один рабочий день
	 */
	public BigDecimal velocitySpPerDay(int referenceWorkingDays) {
		return velocitySpPerSprint.divide(BigDecimal.valueOf(referenceWorkingDays), 6, RoundingMode.HALF_UP);
	}

	public void rename(String name) {
		this.name = name;
	}

	public final void setVelocitySpPerSprint(BigDecimal value) {
		if (value == null || value.signum() <= 0) {
			throw new IllegalArgumentException("Velocity должна быть положительной");
		}
		this.velocitySpPerSprint = value;
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
