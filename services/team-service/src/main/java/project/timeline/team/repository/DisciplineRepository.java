package project.timeline.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.team.domain.Discipline;

import java.util.Optional;
import java.util.UUID;

public interface DisciplineRepository extends JpaRepository<Discipline, UUID> {

	Optional<Discipline> findByCode(String code);

	boolean existsByCode(String code);
}
