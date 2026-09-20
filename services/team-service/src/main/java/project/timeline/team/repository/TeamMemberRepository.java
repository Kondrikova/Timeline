package project.timeline.team.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.team.domain.TeamMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

	@EntityGraph(attributePaths = { "discipline", "vacations" })
	Optional<TeamMember> findByUserId(String userId);

	@EntityGraph(attributePaths = { "discipline", "vacations" })
	Optional<TeamMember> findWithVacationsById(UUID id);

	@EntityGraph(attributePaths = { "discipline", "vacations" })
	List<TeamMember> findAllByOrderByFullNameAsc();

	boolean existsByUserId(String userId);
}
