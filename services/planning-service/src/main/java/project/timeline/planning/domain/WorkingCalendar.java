package project.timeline.planning.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Копия календаря из schedule-service, построенная по событиям.
 *
 * <p>Дублирование намеренное: общая доменная модель между сервисами — прямой путь
 * к распределённому монолиту, и ради тридцати строк расчёта заводить общую
 * библиотеку доменного кода не стоит. Контракт между сервисами — событие, а не
 * разделяемый класс.
 */
public final class WorkingCalendar {

	private final Map<LocalDate, Boolean> overrides;

	public WorkingCalendar(Map<LocalDate, Boolean> overrides) {
		this.overrides = Map.copyOf(overrides);
	}

	public boolean isWorkingDay(LocalDate day) {
		Boolean override = overrides.get(day);
		if (override != null) {
			return override;
		}
		DayOfWeek dayOfWeek = day.getDayOfWeek();
		return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
	}

	public int workingDaysBetween(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			return 0;
		}
		int count = 0;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			if (isWorkingDay(day)) {
				count++;
			}
		}
		return count;
	}
}
