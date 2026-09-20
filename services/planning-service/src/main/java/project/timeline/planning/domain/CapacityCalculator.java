package project.timeline.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Ёмкость роли в спринте.
 *
 * <p>Ёмкость выражается в SP, а отпуска — в днях, поэтому расчёт идёт в два шага:
 * сначала доступность в человеко-днях, затем перевод в SP через velocity.
 *
 * <p>Понижающего коэффициента на встречи и поддержку здесь нет намеренно: velocity
 * калибруется по фактически закрытым спринтам, где всё это уже присутствует, и
 * отдельный множитель дал бы двойной учёт.
 */
public final class CapacityCalculator {

	private final WorkingCalendar calendar;

	public CapacityCalculator(WorkingCalendar calendar) {
		this.calendar = calendar;
	}

	public Capacity calculate(LocalDate sprintStart, LocalDate sprintEnd,
			Collection<PersonAvailability> people, BigDecimal velocitySpPerDay) {

		int availableDays = 0;
		int vacationDays = 0;

		for (PersonAvailability person : people) {
			if (!person.activeDuring(sprintStart, sprintEnd)) {
				continue;
			}
			LocalDate from = person.activeFrom() == null || person.activeFrom().isBefore(sprintStart)
					? sprintStart : person.activeFrom();
			LocalDate to = person.activeTo() == null || person.activeTo().isAfter(sprintEnd)
					? sprintEnd : person.activeTo();

			int personDays = calendar.workingDaysBetween(from, to);
			int personVacation = 0;
			for (DateRange vacation : person.vacations()) {
				DateRange intersection = vacation.intersect(from, to);
				if (intersection != null) {
					personVacation += calendar.workingDaysBetween(intersection.from(), intersection.to());
				}
			}
			availableDays += personDays - personVacation;
			vacationDays += personVacation;
		}

		BigDecimal capacitySp = velocitySpPerDay
				.multiply(BigDecimal.valueOf(availableDays))
				.setScale(2, RoundingMode.HALF_UP);

		return new Capacity(availableDays, vacationDays, capacitySp);
	}

	/**
	 * @param vacationPersonDays хранится не для расчёта, а для объяснения: отвечает
	 *                           на вопрос, почему ёмкость роли в этом спринте ниже
	 *                           обычной
	 */
	public record Capacity(int availablePersonDays, int vacationPersonDays, BigDecimal capacitySp) {
	}
}
