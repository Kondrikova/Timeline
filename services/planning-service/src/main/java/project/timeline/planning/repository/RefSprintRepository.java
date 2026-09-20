package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefSprint;

import java.util.List;
import java.util.UUID;

public interface RefSprintRepository extends JpaRepository<RefSprint, UUID> {

	List<RefSprint> findAllByOrderByNumberAsc();
}
