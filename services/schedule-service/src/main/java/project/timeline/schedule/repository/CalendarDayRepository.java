package project.timeline.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.schedule.domain.CalendarDay;

import java.time.LocalDate;
import java.util.List;

public interface CalendarDayRepository extends JpaRepository<CalendarDay, LocalDate> {

	List<CalendarDay> findAllByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);
}
