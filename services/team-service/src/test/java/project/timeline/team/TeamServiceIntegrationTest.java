package project.timeline.team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.web.DomainException;
import project.timeline.team.domain.Discipline;
import project.timeline.team.domain.TeamMember;
import project.timeline.team.domain.VacationType;
import project.timeline.team.service.DisciplineService;
import project.timeline.team.service.TeamService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamServiceIntegrationTest extends AbstractPostgresTest {

	private static final String ADMIN_USER = "11111111-1111-1111-1111-111111111111";
	private static final String MEMBER_USER = "22222222-2222-2222-2222-222222222222";

	@Autowired
	private TeamService teamService;

	@Autowired
	private DisciplineService disciplineService;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("изменение отпуска пишет бизнес-данные и событие в одной транзакции")
	void writesBusinessDataAndEventAtomically() {
		authenticate(ADMIN_USER, "ADMIN");
		TeamMember member = newMember("petr", MEMBER_USER);

		authenticate(MEMBER_USER, "MEMBER");
		teamService.addVacation(member.getId(),
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		List<String> events = jdbc.queryForList(
				"SELECT event_type FROM outbox WHERE topic = 'team.vacation.v1'", String.class);
		assertThat(events).containsExactly("VacationScheduled");

		Long vacations = jdbc.queryForObject(
				"SELECT count(*) FROM vacation WHERE member_id = ?", Long.class, member.getId());
		assertThat(vacations).isEqualTo(1);
	}

	@Test
	@DisplayName("сотрудник не может править чужой отпуск")
	void rejectsForeignVacation() {
		authenticate(ADMIN_USER, "ADMIN");
		TeamMember other = newMember("olga", "33333333-3333-3333-3333-333333333333");

		authenticate(MEMBER_USER, "MEMBER");

		assertThatThrownBy(() -> teamService.addVacation(other.getId(),
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), VacationType.VACATION))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("только собственный");
	}

	@Test
	@DisplayName("администратор правит отпуск любого сотрудника")
	void allowsAdminToEditAnyVacation() {
		authenticate(ADMIN_USER, "ADMIN");
		TeamMember member = newMember("petr", MEMBER_USER);

		teamService.addVacation(member.getId(),
				LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), VacationType.VACATION);

		assertThat(teamService.requireById(member.getId()).getVacations()).hasSize(1);
	}

	/**
	 * Ключевая проверка: инвариант живёт в БД, поэтому он срабатывает даже в обход
	 * доменной модели — именно так выглядит гонка двух параллельных запросов.
	 */
	@Test
	@DisplayName("СУБД отклоняет пересекающиеся отпуска в обход агрегата")
	void databaseRejectsOverlapBypassingAggregate() {
		authenticate(ADMIN_USER, "ADMIN");
		TeamMember member = newMember("petr", MEMBER_USER);
		insertVacationDirectly(member.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

		assertThatThrownBy(() -> insertVacationDirectly(
				member.getId(), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("смежные отпуска СУБД пропускает")
	void databaseAllowsAdjacentPeriods() {
		authenticate(ADMIN_USER, "ADMIN");
		TeamMember member = newMember("petr", MEMBER_USER);
		insertVacationDirectly(member.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

		insertVacationDirectly(member.getId(), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 20));

		Long count = jdbc.queryForObject(
				"SELECT count(*) FROM vacation WHERE member_id = ?", Long.class, member.getId());
		assertThat(count).isEqualTo(2);
	}

	@Test
	@DisplayName("velocity дисциплины уезжает в событие для планирования")
	void publishesDisciplineVelocity() {
		authenticate(ADMIN_USER, "ADMIN");
		Discipline discipline = disciplineService.create(
				"QA-" + UUID.randomUUID(), "Тестирование", new BigDecimal("12.00"));

		disciplineService.changeVelocity(discipline.getId(), new BigDecimal("15.00"));

		List<String> payloads = jdbc.queryForList(
				"SELECT payload::text FROM outbox WHERE aggregate_id = ? ORDER BY id",
				String.class, discipline.getId());
		assertThat(payloads).hasSize(2);
		assertThat(payloads.get(1)).contains("15.00");
	}

	@Test
	@DisplayName("сотрудник сам указывает профессиональную роль")
	void memberAssignsOwnDiscipline() {
		authenticate(ADMIN_USER, "ADMIN");
		Discipline fe = disciplineService.create("FE-" + UUID.randomUUID(), "Frontend", new BigDecimal("20.00"));

		authenticate(MEMBER_USER, "MEMBER", "Пётр", "Разработчик");
		TeamMember created = teamService.assignMyDiscipline(fe.getId());

		assertThat(created.getUserId()).isEqualTo(MEMBER_USER);
		assertThat(created.getFullName()).isEqualTo("Пётр Разработчик");
		assertThat(created.getDiscipline().getId()).isEqualTo(fe.getId());

		Discipline be = disciplineService.create("BE-" + UUID.randomUUID(), "Backend", new BigDecimal("22.00"));
		authenticate(MEMBER_USER, "MEMBER", "Пётр", "Разработчик");
		TeamMember updated = teamService.assignMyDiscipline(be.getId());
		assertThat(updated.getId()).isEqualTo(created.getId());
		assertThat(updated.getDiscipline().getId()).isEqualTo(be.getId());
	}

	@Transactional
	TeamMember newMember(String name, String userId) {
		Discipline discipline = disciplineService.create(
				"DISC-" + UUID.randomUUID(), name, new BigDecimal("20.00"));
		return teamService.addMember(userId, name, discipline.getId(), false,
				LocalDate.of(2026, 1, 1), null);
	}

	private void insertVacationDirectly(UUID memberId, LocalDate from, LocalDate to) {
		jdbc.update("""
				INSERT INTO vacation (id, member_id, start_date, end_date, type, created_at)
				VALUES (?, ?, ?, ?, 'VACATION', ?)
				""", UUID.randomUUID(), memberId, from, to, java.sql.Timestamp.from(Instant.now()));
	}

	private void authenticate(String userId, String role) {
		authenticate(userId, role, null, null);
	}

	private void authenticate(String userId, String role, String givenName, String familyName) {
		var builder = Jwt.withTokenValue("test-token")
				.header("alg", "none")
				.subject(userId)
				.claim("realm_access", java.util.Map.of("roles", List.of(role)))
				.claim("preferred_username", userId);
		if (givenName != null) {
			builder.claim("given_name", givenName);
		}
		if (familyName != null) {
			builder.claim("family_name", familyName);
		}
		Jwt jwt = builder.build();
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), userId));
	}
}
