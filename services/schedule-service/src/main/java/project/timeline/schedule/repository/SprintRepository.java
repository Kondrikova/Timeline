package project.timeline.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.schedule.domain.Sprint;

import java.util.List;
import java.util.UUID;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

	List<Sprint> findAllByOrderByNumberAsc();

	boolean existsByNumber(int number);
}
