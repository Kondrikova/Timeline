package project.timeline.planning.domain;

import java.time.LocalDate;

public record DateRange(LocalDate from, LocalDate to) {

	public boolean overlaps(LocalDate otherFrom, LocalDate otherTo) {
		return !from.isAfter(otherTo) && !to.isBefore(otherFrom);
	}

	public DateRange intersect(LocalDate otherFrom, LocalDate otherTo) {
		LocalDate start = from.isAfter(otherFrom) ? from : otherFrom;
		LocalDate end = to.isBefore(otherTo) ? to : otherTo;
		return end.isBefore(start) ? null : new DateRange(start, end);
	}
}
