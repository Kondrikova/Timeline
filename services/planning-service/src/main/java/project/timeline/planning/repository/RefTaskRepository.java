package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefTask;

import java.util.UUID;

public interface RefTaskRepository extends JpaRepository<RefTask, UUID> {
}
