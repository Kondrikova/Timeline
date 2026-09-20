package project.timeline.planning.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Доступность сотрудника для расчёта ёмкости.
 *
 * <p>Коэффициента занятости нет: занятость каждого считается полной, снижают её
 * только отпуска и производственный календарь.
 */
public record PersonAvailability(
		UUID memberId,
		UUID disciplineId,
		LocalDate activeFrom,
		LocalDate activeTo,
		List<DateRange> vacations) {

	public boolean activeDuring(LocalDate from, LocalDate to) {
		if (activeFrom != null && activeFrom.isAfter(to)) {
			return false;
		}
		return activeTo == null || !activeTo.isBefore(from);
	}
}
