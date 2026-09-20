package project.timeline.schedule.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Считает рабочие дни в интервале.
 *
 * <p>Правило по умолчанию — будни рабочие, выходные нет; записи календаря его
 * переопределяют в обе стороны, что и позволяет учитывать праздники и переносы.
 * Без этого «рабочие дни спринта» нельзя было бы считать как длительность минус
 * выходные: праздничные периоды съедают до половины итерации.
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
