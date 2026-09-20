package project.timeline.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Итерация — единица времени планирования.
 *
 * <p>Непересечение спринтов обеспечивает ограничение исключения в СУБД: проверка
 * в коде обходится гонкой двух параллельных запросов.
 */
@Entity
@Table(name = "sprint")
public class Sprint {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private int number;

	@Column(nullable = false)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SprintState state;

	@Version
	private Long version;

	protected Sprint() {
	}

	public Sprint(int number, String name, LocalDate startDate, LocalDate endDate) {
		this.id = UUID.randomUUID();
		this.number = number;
		this.name = name;
		this.state = SprintState.PLANNED;
		reschedule(startDate, endDate);
	}

	public final void reschedule(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("Даты спринта обязательны");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Дата окончания спринта раньше даты начала");
		}
		if (state == SprintState.CLOSED) {
			throw new IllegalStateException("Закрытый спринт нельзя перенести");
		}
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changeState(SprintState state) {
		this.state = state;
	}

	/**
	 * Пока сага удаления не завершилась, спринт не должен принимать новую работу:
	 * иначе компенсация вернула бы его с чужими аллокациями.
	 */
	public void markDeleting() {
		if (state == SprintState.DELETING) {
			throw new IllegalStateException("Удаление спринта уже выполняется");
		}
		this.state = SprintState.DELETING;
	}

	public UUID getId() {
		return id;
	}

	public int getNumber() {
		return number;
	}

	public String getName() {
		return name;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public SprintState getState() {
		return state;
	}
}
