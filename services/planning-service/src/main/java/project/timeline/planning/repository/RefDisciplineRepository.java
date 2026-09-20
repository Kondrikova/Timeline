package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefDiscipline;

import java.util.UUID;

public interface RefDisciplineRepository extends JpaRepository<RefDiscipline, UUID> {
}
