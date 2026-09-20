package project.timeline.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Отпуск входит в агрегат {@link TeamMember}: вне сотрудника у него нет
 * жизненного цикла, и транзакционная граница совпадает.
 */
@Entity
@Table(name = "vacation")
public class Vacation {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private TeamMember member;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VacationType type;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Vacation() {
	}

	Vacation(TeamMember member, LocalDate startDate, LocalDate endDate, VacationType type) {
		this.id = UUID.randomUUID();
		this.member = member;
		this.createdAt = Instant.now();
		this.type = type;
		changePeriod(startDate, endDate);
	}

	final void changePeriod(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("Даты отпуска обязательны");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Дата окончания отпуска раньше даты начала");
		}
		this.startDate = startDate;
		this.endDate = endDate;
	}

	void changeType(VacationType type) {
		this.type = type;
	}

	public boolean overlaps(LocalDate from, LocalDate to) {
		return !startDate.isAfter(to) && !endDate.isBefore(from);
	}

	public UUID getId() {
		return id;
	}

	public TeamMember getMember() {
		return member;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public VacationType getType() {
		return type;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
