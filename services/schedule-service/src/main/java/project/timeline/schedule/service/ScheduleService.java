package project.timeline.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.DomainEvent;
import project.timeline.common.events.Topics;
import project.timeline.common.events.schedule.ScheduleEvents;
import project.timeline.common.outbox.OutboxPublisher;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.RequestCorrelationFilter;
import project.timeline.common.web.security.CurrentUser;
import project.timeline.schedule.domain.CalendarDay;
import project.timeline.schedule.domain.Sprint;
import project.timeline.schedule.domain.SprintState;
import project.timeline.schedule.domain.WorkingCalendar;
import project.timeline.schedule.repository.CalendarDayRepository;
import project.timeline.schedule.repository.SprintRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

	private final SprintRepository sprints;
	private final CalendarDayRepository calendarDays;
	private final OutboxPublisher outbox;

	public ScheduleService(SprintRepository sprints, CalendarDayRepository calendarDays, OutboxPublisher outbox) {
		this.sprints = sprints;
		this.calendarDays = calendarDays;
		this.outbox = outbox;
	}

	@Transactional(readOnly = true)
	public List<Sprint> findAll() {
		return sprints.findAllByOrderByNumberAsc();
	}

	@Transactional(readOnly = true)
	public Sprint require(UUID id) {
		return sprints.findById(id)
				.orElseThrow(() -> DomainException.notFound("SPRINT_NOT_FOUND", "Спринт не найден: " + id));
	}

	@Transactional
	public Sprint create(int number, String name, LocalDate start, LocalDate end) {
		if (sprints.existsByNumber(number)) {
			throw DomainException.conflict("SPRINT_NUMBER_TAKEN", "Спринт с номером " + number + " уже существует");
		}
		Sprint sprint = translate(() -> sprints.save(new Sprint(number, name, start, end)));
		publish(ScheduleEvents.SPRINT_CREATED, sprint);
		return sprint;
	}

	@Transactional
	public Sprint reschedule(UUID id, String name, LocalDate start, LocalDate end) {
		Sprint sprint = require(id);
		translate(() -> {
			sprint.reschedule(start, end);
			if (name != null && !name.isBlank()) {
				sprint.rename(name);
			}
			return sprint;
		});
		publish(ScheduleEvents.SPRINT_RESCHEDULED, sprint);
		return sprint;
	}

	@Transactional
	public Sprint changeState(UUID id, SprintState state) {
		Sprint sprint = require(id);
		sprint.changeState(state);
		publish(ScheduleEvents.SPRINT_RESCHEDULED, sprint);
		return sprint;
	}

	@Transactional(readOnly = true)
	public WorkingCalendar workingCalendar() {
		Map<LocalDate, Boolean> overrides = calendarDays.findAll().stream()
				.collect(Collectors.toMap(CalendarDay::getDay, CalendarDay::isWorking));
		return new WorkingCalendar(overrides);
	}

	@Transactional(readOnly = true)
	public int workingDays(Sprint sprint) {
		return workingCalendar().workingDaysBetween(sprint.getStartDate(), sprint.getEndDate());
	}

	@Transactional(readOnly = true)
	public List<CalendarDay> calendar(LocalDate from, LocalDate to) {
		return calendarDays.findAllByDayBetweenOrderByDayAsc(from, to);
	}

	/**
	 * Календарь обновляется пачкой и порождает одно событие: изменение праздников
	 * затрагивает диапазон дат, а событие на каждый день вызвало бы лавину
	 * пересчётов ёмкости у потребителя.
	 */
	@Transactional
	public List<CalendarDay> updateCalendar(Map<LocalDate, Boolean> days, String comment) {
		Map<LocalDate, CalendarDay> existing = calendarDays.findAllById(days.keySet()).stream()
				.collect(Collectors.toMap(CalendarDay::getDay, Function.identity()));

		Map<LocalDate, CalendarDay> saved = new LinkedHashMap<>();
		days.forEach((day, working) -> {
			CalendarDay entity = existing.get(day);
			if (entity == null) {
				entity = new CalendarDay(day, working, comment);
			}
			else {
				entity.change(working, comment);
			}
			saved.put(day, entity);
		});
		List<CalendarDay> result = calendarDays.saveAll(saved.values());

		outbox.publish(Topics.SCHEDULE_CALENDAR, DomainEvent.of(
				ScheduleEvents.CALENDAR_UPDATED,
				"WorkingCalendar",
				// У календаря нет естественного идентификатора агрегата, поэтому
				// ключ фиксирован: события календаря должны применяться по порядку.
				CALENDAR_AGGREGATE_ID,
				CurrentUser.requireUserId(),
				RequestCorrelationFilter.currentTraceId(),
				new ScheduleEvents.CalendarPayload(result.stream()
						.map(day -> new ScheduleEvents.CalendarDayPayload(day.getDay(), day.isWorking()))
						.toList())));
		return result;
	}

	static final UUID CALENDAR_AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-00000000ca1e");

	void publish(String eventType, Sprint sprint) {
		outbox.publish(Topics.SCHEDULE_SPRINT, DomainEvent.of(
				eventType,
				"Sprint",
				sprint.getId(),
				CurrentUser.requireUserId(),
				RequestCorrelationFilter.currentTraceId(),
				new ScheduleEvents.SprintPayload(
						sprint.getId(),
						sprint.getNumber(),
						sprint.getName(),
						sprint.getStartDate(),
						sprint.getEndDate(),
						sprint.getState().name())));
	}

	private <T> T translate(java.util.function.Supplier<T> action) {
		try {
			return action.get();
		}
		catch (IllegalStateException e) {
			throw DomainException.conflict("SPRINT_STATE_INVALID", e.getMessage());
		}
		catch (IllegalArgumentException e) {
			throw DomainException.badRequest("SPRINT_INVALID", e.getMessage());
		}
	}
}
