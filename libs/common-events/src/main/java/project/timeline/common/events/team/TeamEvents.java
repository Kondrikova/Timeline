package project.timeline.common.events.team;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Полезные нагрузки событий, публикуемых team-service. */
public final class TeamEvents {

	public static final String MEMBER_JOINED = "MemberJoined";
	public static final String MEMBER_UPDATED = "MemberUpdated";
	public static final String MEMBER_DEACTIVATED = "MemberDeactivated";
	public static final String VACATION_SCHEDULED = "VacationScheduled";
	public static final String VACATION_CHANGED = "VacationChanged";
	public static final String VACATION_CANCELLED = "VacationCancelled";
	public static final String DISCIPLINE_UPDATED = "DisciplineUpdated";

	public record MemberPayload(
			UUID memberId,
			String userId,
			String fullName,
			UUID disciplineId,
			String disciplineCode,
			boolean lead,
			LocalDate activeFrom,
			LocalDate activeTo) {
	}

	public record VacationPayload(
			UUID vacationId,
			UUID memberId,
			UUID disciplineId,
			LocalDate startDate,
			LocalDate endDate,
			String type) {
	}

	/**
	 * Velocity едет в планирование вместе с дисциплиной: без неё доступность в
	 * человеко-днях невозможно перевести в SP.
	 */
	public record DisciplinePayload(
			UUID disciplineId,
			String code,
			String name,
			BigDecimal velocitySpPerSprint) {
	}

	private TeamEvents() {
	}
}
