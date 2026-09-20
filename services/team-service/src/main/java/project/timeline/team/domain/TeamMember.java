package project.timeline.team.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Корень агрегата «сотрудник команды».
 *
 * <p>Коэффициента занятости в модели нет: занятость каждого сотрудника считается
 * полной, доступность снижают только отпуска и производственный календарь.
 */
@Entity
@Table(name = "team_member")
public class TeamMember {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private String userId;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "discipline_id", nullable = false)
	private Discipline discipline;

	@Column(name = "is_lead", nullable = false)
	private boolean lead;

	@Column(name = "active_from", nullable = false)
	private LocalDate activeFrom;

	/** {@code null} означает, что сотрудник в команде и дата ухода не назначена. */
	@Column(name = "active_to")
	private LocalDate activeTo;

	@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Vacation> vacations = new ArrayList<>();

	@Version
	private Long version;

	protected TeamMember() {
	}

	public TeamMember(String userId, String fullName, Discipline discipline, boolean lead,
			LocalDate activeFrom, LocalDate activeTo) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.fullName = fullName;
		this.discipline = discipline;
		this.lead = lead;
		this.activeFrom = activeFrom;
		this.activeTo = activeTo;
	}

	public Vacation addVacation(LocalDate startDate, LocalDate endDate, VacationType type) {
		requireNoOverlap(startDate, endDate, null);
		Vacation vacation = new Vacation(this, startDate, endDate, type);
		vacations.add(vacation);
		return vacation;
	}

	public Vacation changeVacation(UUID vacationId, LocalDate startDate, LocalDate endDate, VacationType type) {
		Vacation vacation = requireVacation(vacationId);
		requireNoOverlap(startDate, endDate, vacationId);
		vacation.changePeriod(startDate, endDate);
		vacation.changeType(type);
		return vacation;
	}

	public Vacation removeVacation(UUID vacationId) {
		Vacation vacation = requireVacation(vacationId);
		vacations.remove(vacation);
		return vacation;
	}

	public void deactivate(LocalDate lastDay) {
		this.activeTo = lastDay;
	}

	public void update(String fullName, Discipline discipline, boolean lead,
			LocalDate activeFrom, LocalDate activeTo) {
		this.fullName = fullName;
		this.discipline = discipline;
		this.lead = lead;
		this.activeFrom = activeFrom;
		this.activeTo = activeTo;
	}

	/** Смена профессиональной роли (дисциплины) без изменения прочих полей. */
	public void changeDiscipline(Discipline discipline) {
		this.discipline = discipline;
	}

	public void rename(String fullName) {
		this.fullName = fullName;
	}

	public boolean isOwnedBy(String candidateUserId) {
		return userId.equals(candidateUserId);
	}

	/**
	 * Дублирует ограничение исключения в БД, чтобы вернуть внятную ошибку вместо
	 * нарушения constraint. Источником истины остаётся БД: только она защищает от
	 * гонки двух параллельных запросов.
	 */
	private void requireNoOverlap(LocalDate startDate, LocalDate endDate, UUID excludedId) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			return;
		}
		boolean overlapping = vacations.stream()
				.filter(existing -> !existing.getId().equals(excludedId))
				.anyMatch(existing -> existing.overlaps(startDate, endDate));
		if (overlapping) {
			throw new IllegalStateException("Период пересекается с уже сохранённым отпуском");
		}
	}

	private Vacation requireVacation(UUID vacationId) {
		return findVacation(vacationId)
				.orElseThrow(() -> new IllegalArgumentException("Отпуск не найден: " + vacationId));
	}

	public Optional<Vacation> findVacation(UUID vacationId) {
		return vacations.stream().filter(vacation -> vacation.getId().equals(vacationId)).findFirst();
	}

	public UUID getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getFullName() {
		return fullName;
	}

	public Discipline getDiscipline() {
		return discipline;
	}

	public boolean isLead() {
		return lead;
	}

	public LocalDate getActiveFrom() {
		return activeFrom;
	}

	public LocalDate getActiveTo() {
		return activeTo;
	}

	public List<Vacation> getVacations() {
		return List.copyOf(vacations);
	}
}
