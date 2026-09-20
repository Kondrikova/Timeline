package project.timeline.team.service;

import org.springframework.stereotype.Component;
import project.timeline.common.events.DomainEvent;
import project.timeline.common.events.Topics;
import project.timeline.common.events.team.TeamEvents;
import project.timeline.common.outbox.OutboxPublisher;
import project.timeline.common.web.RequestCorrelationFilter;
import project.timeline.team.domain.Discipline;
import project.timeline.team.domain.TeamMember;
import project.timeline.team.domain.Vacation;

@Component
public class TeamEventPublisher {

	private final OutboxPublisher outbox;

	public TeamEventPublisher(OutboxPublisher outbox) {
		this.outbox = outbox;
	}

	public void memberEvent(String eventType, TeamMember member, String actorId) {
		outbox.publish(Topics.TEAM_MEMBER, DomainEvent.of(
				eventType,
				"TeamMember",
				member.getId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				payload(member)));
	}

	/**
	 * Ключом партиции служит идентификатор сотрудника, а не отпуска: порядок важен в
	 * пределах агрегата, иначе отмена могла бы примениться раньше создания.
	 */
	public void vacationEvent(String eventType, Vacation vacation, String actorId) {
		TeamMember member = vacation.getMember();
		outbox.publish(Topics.TEAM_VACATION, DomainEvent.of(
				eventType,
				"TeamMember",
				member.getId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				new TeamEvents.VacationPayload(
						vacation.getId(),
						member.getId(),
						member.getDiscipline().getId(),
						vacation.getStartDate(),
						vacation.getEndDate(),
						vacation.getType().name())));
	}

	public void disciplineEvent(Discipline discipline, String actorId) {
		outbox.publish(Topics.TEAM_MEMBER, DomainEvent.of(
				TeamEvents.DISCIPLINE_UPDATED,
				"Discipline",
				discipline.getId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				new TeamEvents.DisciplinePayload(
						discipline.getId(),
						discipline.getCode(),
						discipline.getName(),
						discipline.getVelocitySpPerSprint())));
	}

	private TeamEvents.MemberPayload payload(TeamMember member) {
		return new TeamEvents.MemberPayload(
				member.getId(),
				member.getUserId(),
				member.getFullName(),
				member.getDiscipline().getId(),
				member.getDiscipline().getCode(),
				member.isLead(),
				member.getActiveFrom(),
				member.getActiveTo());
	}
}
