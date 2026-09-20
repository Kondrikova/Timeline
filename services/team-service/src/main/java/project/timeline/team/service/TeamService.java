package project.timeline.team.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.team.TeamEvents;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.security.CurrentUser;
import project.timeline.team.api.dto.TeamDtos.DirectoryUserResponse;
import project.timeline.team.domain.Discipline;
import project.timeline.team.domain.TeamMember;
import project.timeline.team.domain.Vacation;
import project.timeline.team.domain.VacationType;
import project.timeline.team.keycloak.KeycloakDirectoryClient;
import project.timeline.team.keycloak.KeycloakUser;
import project.timeline.team.repository.TeamMemberRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamService {

	private final TeamMemberRepository members;
	private final DisciplineService disciplines;
	private final TeamEventPublisher events;
	private final KeycloakDirectoryClient keycloak;

	public TeamService(TeamMemberRepository members, DisciplineService disciplines, TeamEventPublisher events,
			KeycloakDirectoryClient keycloak) {
		this.members = members;
		this.disciplines = disciplines;
		this.events = events;
		this.keycloak = keycloak;
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

	/**
	 * Текущий пользователь сам указывает профессиональную роль: создаёт профиль
	 * команды при первом выборе или меняет дисциплину у уже существующего.
	 */
	@Transactional
	public TeamMember assignMyDiscipline(UUID disciplineId) {
		String userId = CurrentUser.requireUserId();
		String fullName = CurrentUser.displayName();
		Discipline discipline = disciplines.require(disciplineId);
		Optional<TeamMember> existing = members.findByUserId(userId);
		if (existing.isPresent()) {
			TeamMember member = existing.get();
			member.rename(fullName);
			member.changeDiscipline(discipline);
			events.memberEvent(TeamEvents.MEMBER_UPDATED, member, userId);
			return member;
		}
		TeamMember created = members.save(
				new TeamMember(userId, fullName, discipline, false, LocalDate.now(), null));
		events.memberEvent(TeamEvents.MEMBER_JOINED, created, userId);
		return created;
	}

	/**
	 * Админ назначает роль пользователю из каталога Keycloak без ручного ввода sub.
	 */
	@Transactional
	public TeamMember assignDirectoryDiscipline(String userId, UUID disciplineId, boolean lead) {
		KeycloakUser user = keycloak.findById(userId)
				.orElseThrow(() -> DomainException.notFound("KEYCLOAK_USER_NOT_FOUND",
						"Пользователь не найден в Keycloak: " + userId));
		Discipline discipline = disciplines.require(disciplineId);
		Optional<TeamMember> existing = members.findByUserId(userId);
		if (existing.isPresent()) {
			TeamMember member = existing.get();
			member.update(user.displayName(), discipline, lead, member.getActiveFrom(), member.getActiveTo());
			events.memberEvent(TeamEvents.MEMBER_UPDATED, member, CurrentUser.requireUserId());
			return member;
		}
		TeamMember created = members.save(
				new TeamMember(userId, user.displayName(), discipline, lead, LocalDate.now(), null));
		events.memberEvent(TeamEvents.MEMBER_JOINED, created, CurrentUser.requireUserId());
		return created;
	}

	@Transactional(readOnly = true)
	public List<DirectoryUserResponse> directory() {
		Map<String, TeamMember> byUserId = findAll().stream()
				.collect(Collectors.toMap(TeamMember::getUserId, Function.identity(), (a, b) -> a));
		return keycloak.listUsers().stream()
				.sorted(Comparator.comparing(KeycloakUser::displayName, String.CASE_INSENSITIVE_ORDER))
				.map(user -> {
					TeamMember member = byUserId.get(user.id());
					if (member == null) {
						return new DirectoryUserResponse(user.id(), user.username(), user.displayName(),
								user.email(), false, null, null, null, false);
					}
					return new DirectoryUserResponse(user.id(), user.username(), member.getFullName(),
							user.email(), true, member.getId(), member.getDiscipline().getId(),
							member.getDiscipline().getCode(), member.isLead());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public Optional<TeamMember> findCurrentOptional() {
		return members.findByUserId(CurrentUser.requireUserId());
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
