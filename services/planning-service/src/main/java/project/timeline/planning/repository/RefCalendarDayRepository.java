package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefCalendarDay;

import java.time.LocalDate;

public interface RefCalendarDayRepository extends JpaRepository<RefCalendarDay, LocalDate> {
}
