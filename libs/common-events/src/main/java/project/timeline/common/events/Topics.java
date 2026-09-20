package project.timeline.common.events;

/** Имена топиков. Версия в имени: несовместимое изменение схемы создаёт новый топик. */
public final class Topics {

	public static final String TEAM_MEMBER = "team.member.v1";
	public static final String TEAM_VACATION = "team.vacation.v1";
	public static final String SCHEDULE_SPRINT = "schedule.sprint.v1";
	public static final String SCHEDULE_CALENDAR = "schedule.calendar.v1";
	public static final String BACKLOG_TASK = "backlog.task.v1";
	public static final String BACKLOG_LINK = "backlog.link.v1";
	public static final String PLANNING_ALLOCATION = "planning.allocation.v1";
	public static final String PLANNING_CONFLICT = "planning.conflict.v1";

	private Topics() {
	}
}
