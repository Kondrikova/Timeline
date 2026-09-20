package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefVacation;

import java.util.List;
import java.util.UUID;

public interface RefVacationRepository extends JpaRepository<RefVacation, UUID> {

	List<RefVacation> findAllByMemberId(UUID memberId);
}
