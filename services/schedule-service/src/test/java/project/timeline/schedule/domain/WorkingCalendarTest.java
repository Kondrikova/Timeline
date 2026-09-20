package project.timeline.schedule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingCalendarTest {

	@Test
	@DisplayName("по умолчанию рабочими считаются будни")
	void countsWeekdaysByDefault() {
		WorkingCalendar calendar = new WorkingCalendar(Map.of());

		// 2026-06-01 — понедельник, две полные недели.
		int days = calendar.workingDaysBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14));

		assertThat(days).isEqualTo(10);
	}

	@Test
	@DisplayName("праздники уменьшают число рабочих дней")
	void subtractsHolidays() {
		WorkingCalendar calendar = new WorkingCalendar(Map.of(
				LocalDate.of(2026, 6, 10), false,
				LocalDate.of(2026, 6, 11), false));

		int days = calendar.workingDaysBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14));

		assertThat(days).isEqualTo(8);
	}

	@Test
	@DisplayName("перенесённый выходной становится рабочим днём")
	void countsTransferredWorkingSaturday() {
		WorkingCalendar calendar = new WorkingCalendar(Map.of(LocalDate.of(2026, 6, 6), true));

		int days = calendar.workingDaysBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14));

		assertThat(days).isEqualTo(11);
	}

	@Test
	@DisplayName("перевёрнутый интервал даёт ноль, а не отрицательное значение")
	void returnsZeroForInvertedRange() {
		WorkingCalendar calendar = new WorkingCalendar(Map.of());

		assertThat(calendar.workingDaysBetween(LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 1)))
				.isZero();
	}
}
