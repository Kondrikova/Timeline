package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.SprintCapacity;

import java.util.List;

public interface SprintCapacityRepository extends JpaRepository<SprintCapacity, SprintCapacity.Key> {

	List<SprintCapacity> findAll();
}
