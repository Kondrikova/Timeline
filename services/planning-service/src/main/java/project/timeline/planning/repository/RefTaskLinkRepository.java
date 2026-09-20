package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefTaskLink;

import java.util.UUID;

public interface RefTaskLinkRepository extends JpaRepository<RefTaskLink, UUID> {
}
