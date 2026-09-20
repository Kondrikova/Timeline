package project.timeline.team.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.team.TeamEvents;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.security.CurrentUser;
import project.timeline.team.domain.Discipline;
import project.timeline.team.domain.TeamMember;
import project.timeline.team.domain.Vacation;
import project.timeline.team.domain.VacationType;
import project.timeline.team.repository.TeamMemberRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

	private final TeamMemberRepository members;
	private final DisciplineService disciplines;
	private final TeamEventPublisher events;

	public TeamService(TeamMemberRepository members, DisciplineService disciplines, TeamEventPublisher events) {
		this.members = members;
		this.disciplines = disciplines;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<TeamMember> findAll() {
		return members.findAllByOrderByFullNameAsc();
	}

	@Transactional(readOnly = true)
	public TeamMember requireById(UUID id) {
		return members.findWithVacationsById(id)
				.orElseThrow(() -> DomainException.notFound("MEMBER_NOT_FOUND", "Сотрудник не найден: " + id));
	}

	@Transactional(readOnly = true)
	public TeamMember requireCurrent() {
		String userId = CurrentUser.requireUserId();
		return members.findByUserId(userId)
				.orElseThrow(() -> DomainException.notFound("MEMBER_NOT_FOUND",
						"Учётная запись не связана с сотрудником команды"));
	}

	@Transactional
	public TeamMember addMember(String userId, String fullName, UUID disciplineId, boolean lead,
			LocalDate activeFrom, LocalDate activeTo) {
		if (members.existsByUserId(userId)) {
			throw DomainException.conflict("MEMBER_EXISTS", "Сотрудник с этой учётной записью уже заведён");
		}
		Discipline discipline = disciplines.require(disciplineId);
		TeamMember member = members.save(
				new TeamMember(userId, fullName, discipline, lead, activeFrom, activeTo));
		events.memberEvent(TeamEvents.MEMBER_JOINED, member, CurrentUser.requireUserId());
		return member;
	}

	@Transactional
	public TeamMember updateMember(UUID id, String fullName, UUID disciplineId, boolean lead,
			LocalDate activeFrom, LocalDate activeTo) {
		TeamMember member = requireById(id);
		member.update(fullName, disciplines.require(disciplineId), lead, activeFrom, activeTo);
		events.memberEvent(TeamEvents.MEMBER_UPDATED, member, CurrentUser.requireUserId());
		return member;
	}

	@Transactional
	public TeamMember deactivateMember(UUID id, LocalDate lastDay) {
		TeamMember member = requireById(id);
		member.deactivate(lastDay);
		events.memberEvent(TeamEvents.MEMBER_DEACTIVATED, member, CurrentUser.requireUserId());
		return member;
	}

	@Transactional
	public Vacation addVacation(UUID memberId, LocalDate start, LocalDate end, VacationType type) {
		TeamMember member = requireEditableMember(memberId);
		Vacation vacation = translateDomainErrors(() -> member.addVacation(start, end, type));
		events.vacationEvent(TeamEvents.VACATION_SCHEDULED, vacation, CurrentUser.requireUserId());
		return vacation;
	}

	@Transactional
	public Vacation changeVacation(UUID memberId, UUID vacationId, LocalDate start, LocalDate end, VacationType type) {
		TeamMember member = requireEditableMember(memberId);
		Vacation vacation = translateDomainErrors(() -> member.changeVacation(vacationId, start, end, type));
		events.vacationEvent(TeamEvents.VACATION_CHANGED, vacation, CurrentUser.requireUserId());
		return vacation;
	}

	@Transactional
	public void cancelVacation(UUID memberId, UUID vacationId) {
		TeamMember member = requireEditableMember(memberId);
		Vacation vacation = translateDomainErrors(() -> member.removeVacation(vacationId));
		events.vacationEvent(TeamEvents.VACATION_CANCELLED, vacation, CurrentUser.requireUserId());
	}

	/**
	 * Правило «сотрудник правит только свой отпуск» проверяется здесь, а не в шлюзе
	 * и не в отдельном сервисе авторизации: оно опирается на связь учётной записи с
	 * сотрудником, которая известна только этому сервису.
	 */
	private TeamMember requireEditableMember(UUID memberId) {
		TeamMember member = requireById(memberId);
		if (!CurrentUser.isAdmin() && !member.isOwnedBy(CurrentUser.requireUserId())) {
			throw DomainException.forbidden("NOT_OWN_VACATION", "Изменять можно только собственный отпуск");
		}
		return member;
	}

	private <T> T translateDomainErrors(java.util.function.Supplier<T> action) {
		try {
			return action.get();
		}
		catch (IllegalStateException e) {
			throw DomainException.conflict("VACATION_OVERLAP", e.getMessage());
		}
		catch (IllegalArgumentException e) {
			throw DomainException.badRequest("VACATION_INVALID", e.getMessage());
		}
	}
}
