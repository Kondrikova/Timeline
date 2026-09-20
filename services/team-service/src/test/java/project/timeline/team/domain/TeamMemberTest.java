package project.timeline.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamMemberTest {

	private final Discipline backend = new Discipline("BACKEND", "Разработка", new BigDecimal("20"));

	private TeamMember member() {
		return new TeamMember("user-1", "Пётр", backend, false, LocalDate.of(2026, 1, 1), null);
	}

	@Test
	@DisplayName("отпуск добавляется и попадает в агрегат")
	void addsVacation() {
		TeamMember member = member();

		Vacation vacation = member.addVacation(
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		assertThat(member.getVacations()).containsExactly(vacation);
		assertThat(vacation.getMember()).isSameAs(member);
	}

	@Test
	@DisplayName("пересекающийся отпуск отклоняется")
	void rejectsOverlappingVacation() {
		TeamMember member = member();
		member.addVacation(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		assertThatThrownBy(() -> member.addVacation(
				LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20), VacationType.VACATION))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("смежные отпуска без наложения допустимы")
	void allowsAdjacentVacations() {
		TeamMember member = member();
		member.addVacation(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		member.addVacation(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 20), VacationType.DAYOFF);

		assertThat(member.getVacations()).hasSize(2);
	}

	@Test
	@DisplayName("при изменении отпуск не пересекается сам с собой")
	void ignoresSelfWhenChangingPeriod() {
		TeamMember member = member();
		Vacation vacation = member.addVacation(
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		member.changeVacation(vacation.getId(),
				LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 18), VacationType.VACATION);

		assertThat(vacation.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 5));
		assertThat(vacation.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 18));
	}

	@Test
	@DisplayName("дата окончания раньше начала отклоняется")
	void rejectsInvertedPeriod() {
		TeamMember member = member();

		assertThatThrownBy(() -> member.addVacation(
				LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 1), VacationType.VACATION))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("отпуск отменяется")
	void cancelsVacation() {
		TeamMember member = member();
		Vacation vacation = member.addVacation(
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14), VacationType.VACATION);

		member.removeVacation(vacation.getId());

		assertThat(member.getVacations()).isEmpty();
	}

	@Test
	@DisplayName("владение ресурсом определяется учётной записью")
	void checksOwnership() {
		TeamMember member = member();

		assertThat(member.isOwnedBy("user-1")).isTrue();
		assertThat(member.isOwnedBy("user-2")).isFalse();
	}
}
