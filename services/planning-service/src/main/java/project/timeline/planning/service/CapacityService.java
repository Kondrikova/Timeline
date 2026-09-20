package project.timeline.planning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.planning.domain.CapacityCalculator;
import project.timeline.planning.domain.DateRange;
import project.timeline.planning.domain.PersonAvailability;
import project.timeline.planning.domain.WorkingCalendar;
import project.timeline.planning.replica.RefCalendarDay;
import project.timeline.planning.replica.RefDiscipline;
import project.timeline.planning.replica.RefMember;
import project.timeline.planning.replica.RefSprint;
import project.timeline.planning.replica.RefVacation;
import project.timeline.planning.replica.SprintCapacity;
import project.timeline.planning.repository.RefCalendarDayRepository;
import project.timeline.planning.repository.RefDisciplineRepository;
import project.timeline.planning.repository.RefMemberRepository;
import project.timeline.planning.repository.RefSprintRepository;
import project.timeline.planning.repository.RefVacationRepository;
import project.timeline.planning.repository.SprintCapacityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Пересчёт ёмкости по всем спринтам и ролям.
 *
 * <p>Расчёт живёт здесь, а не в сервисе команды: он опирается на состав команды,
 * отпуска, даты спринтов и календарь одновременно, а единственный потребитель —
 * планирование. Вынесение в отдельный сервис добавило бы сетевой вызов в самый
 * горячий путь, не дав ни одного независимого потребителя.
 */
@Service
public class CapacityService {

	private final RefDisciplineRepository disciplines;
	private final RefMemberRepository members;
	private final RefVacationRepository vacations;
	private final RefSprintRepository sprints;
	private final RefCalendarDayRepository calendarDays;
	private final SprintCapacityRepository capacities;
	private final int referenceWorkingDays;

	public CapacityService(RefDisciplineRepository disciplines, RefMemberRepository members,
			RefVacationRepository vacations, RefSprintRepository sprints,
			RefCalendarDayRepository calendarDays, SprintCapacityRepository capacities,
			@Value("${timeline.planning.reference-working-days:10}") int referenceWorkingDays) {
		this.disciplines = disciplines;
		this.members = members;
		this.vacations = vacations;
		this.sprints = sprints;
		this.calendarDays = calendarDays;
		this.capacities = capacities;
		this.referenceWorkingDays = referenceWorkingDays;
	}

	@Transactional(readOnly = true)
	public WorkingCalendar workingCalendar() {
		Map<java.time.LocalDate, Boolean> overrides = calendarDays.findAll().stream()
				.collect(Collectors.toMap(RefCalendarDay::getDay, RefCalendarDay::isWorking));
		return new WorkingCalendar(overrides);
	}

	@Transactional
	public List<SprintCapacity> recalculateAll() {
		CapacityCalculator calculator = new CapacityCalculator(workingCalendar());

		Map<UUID, List<DateRange>> vacationsByMember = vacations.findAll().stream()
				.collect(Collectors.groupingBy(RefVacation::getMemberId,
						Collectors.mapping(
								vacation -> new DateRange(vacation.getStartDate(), vacation.getEndDate()),
								Collectors.toList())));

		Map<UUID, List<PersonAvailability>> peopleByDiscipline = members.findAll().stream()
				.map(member -> toAvailability(member, vacationsByMember))
				.collect(Collectors.groupingBy(PersonAvailability::disciplineId));

		List<SprintCapacity> recalculated = new ArrayList<>();
		for (RefSprint sprint : sprints.findAllByOrderByNumberAsc()) {
			for (RefDiscipline discipline : disciplines.findAll()) {
				CapacityCalculator.Capacity capacity = calculator.calculate(
						sprint.getStartDate(),
						sprint.getEndDate(),
						peopleByDiscipline.getOrDefault(discipline.getId(), List.of()),
						discipline.velocitySpPerDay(referenceWorkingDays));
				recalculated.add(new SprintCapacity(sprint.getId(), discipline.getId(),
						capacity.availablePersonDays(), capacity.vacationPersonDays(), capacity.capacitySp()));
			}
		}

		// Полная замена, а не выборочное обновление: ёмкость — производная
		// величина, и пересчёт от исходных данных дешевле, чем поиск того, что
		// именно изменилось.
		capacities.deleteAllInBatch();
		return capacities.saveAll(recalculated);
	}

	@Transactional(readOnly = true)
	public List<SprintCapacity> current() {
		return capacities.findAll();
	}

	private PersonAvailability toAvailability(RefMember member, Map<UUID, List<DateRange>> vacationsByMember) {
		return new PersonAvailability(
				member.getId(),
				member.getDisciplineId(),
				member.getActiveFrom(),
				member.getActiveTo(),
				vacationsByMember.getOrDefault(member.getId(), List.of()));
	}
}
