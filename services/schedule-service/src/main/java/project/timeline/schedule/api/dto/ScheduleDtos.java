package project.timeline.schedule.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import project.timeline.schedule.domain.CalendarDay;
import project.timeline.schedule.domain.Sprint;
import project.timeline.schedule.domain.SprintState;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class ScheduleDtos {

	public record SprintRequest(
			@Positive int number,
			@NotBlank String name,
			@NotNull LocalDate startDate,
			@NotNull LocalDate endDate) {
	}

	public record RescheduleRequest(
			String name,
			@NotNull LocalDate startDate,
			@NotNull LocalDate endDate) {
	}

	public record SprintStateRequest(@NotNull SprintState state) {
	}

	public record SprintResponse(
			UUID id,
			int number,
			String name,
			LocalDate startDate,
			LocalDate endDate,
			SprintState state,
			int workingDays) {

		public static SprintResponse of(Sprint sprint, int workingDays) {
			return new SprintResponse(sprint.getId(), sprint.getNumber(), sprint.getName(),
					sprint.getStartDate(), sprint.getEndDate(), sprint.getState(), workingDays);
		}
	}

	public record SprintDeletedResponse(UUID sprintId, int releasedTasks) {
	}

	public record CalendarUpdateRequest(
			@NotEmpty Map<LocalDate, Boolean> days,
			String comment) {
	}

	public record CalendarDayResponse(LocalDate day, boolean working, String comment) {

		public static CalendarDayResponse of(CalendarDay day) {
			return new CalendarDayResponse(day.getDay(), day.isWorking(), day.getComment());
		}
	}

	private ScheduleDtos() {
	}
}
