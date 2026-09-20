package project.timeline.common.events.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Полезные нагрузки событий, публикуемых schedule-service. */
public final class ScheduleEvents {

	public static final String SPRINT_CREATED = "SprintCreated";
	public static final String SPRINT_RESCHEDULED = "SprintRescheduled";
	public static final String SPRINT_DELETED = "SprintDeleted";
	public static final String CALENDAR_UPDATED = "CalendarUpdated";

	public record SprintPayload(
			UUID sprintId,
			int number,
			String name,
			LocalDate startDate,
			LocalDate endDate,
			String state) {
	}

	public record CalendarDayPayload(LocalDate day, boolean working) {
	}

	/**
	 * Календарь едет пачкой: изменение праздников затрагивает сразу диапазон дат, и
	 * событие на каждый день породило бы лавину пересчётов ёмкости.
	 */
	public record CalendarPayload(List<CalendarDayPayload> days) {
	}

	private ScheduleEvents() {
	}
}
