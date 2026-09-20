package project.timeline.planning.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityCalculatorTest {

	private final WorkingCalendar calendar = new WorkingCalendar(Map.of(
			LocalDate.of(2026, 6, 12), false));

	@Test
	@DisplayName("ёмкость равна velocity на доступные человеко-дни")
	void multipliesVelocityByAvailableDays() {
		UUID memberId = UUID.randomUUID();
		UUID disciplineId = UUID.randomUUID();
		PersonAvailability person = new PersonAvailability(memberId, disciplineId,
				LocalDate.of(2026, 1, 1), null, List.of());

		CapacityCalculator.Capacity capacity = new CapacityCalculator(calendar).calculate(
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14),
				List.of(person), new BigDecimal("2"));

		// 1–14 июня 2026: 10 будней минус праздник 12 июня = 9
		assertThat(capacity.availablePersonDays()).isEqualTo(9);
		assertThat(capacity.vacationPersonDays()).isZero();
		assertThat(capacity.capacitySp()).isEqualByComparingTo("18.00");
	}

	@Test
	@DisplayName("отпуск уменьшает ёмкость и попадает в объясняющее поле")
	void subtractsVacationDays() {
		UUID memberId = UUID.randomUUID();
		UUID disciplineId = UUID.randomUUID();
		PersonAvailability person = new PersonAvailability(memberId, disciplineId,
				LocalDate.of(2026, 1, 1), null,
				List.of(new DateRange(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 12))));

		CapacityCalculator.Capacity capacity = new CapacityCalculator(calendar).calculate(
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14),
				List.of(person), new BigDecimal("2"));

		// Отпуск 8–12: пн–пт, но 12 уже праздник → 4 отпускных рабочих дня
		assertThat(capacity.vacationPersonDays()).isEqualTo(4);
		assertThat(capacity.availablePersonDays()).isEqualTo(5);
		assertThat(capacity.capacitySp()).isEqualByComparingTo("10.00");
	}

	@Test
	@DisplayName("неактивный в спринте сотрудник не учитывается")
	void ignoresInactiveMember() {
		PersonAvailability person = new PersonAvailability(UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.of(2026, 7, 1), null, List.of());

		CapacityCalculator.Capacity capacity = new CapacityCalculator(calendar).calculate(
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14),
				List.of(person), new BigDecimal("2"));

		assertThat(capacity.availablePersonDays()).isZero();
		assertThat(capacity.capacitySp()).isEqualByComparingTo("0.00");
	}
}
