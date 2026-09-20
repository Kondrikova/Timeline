package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefMember;

import java.util.UUID;

public interface RefMemberRepository extends JpaRepository<RefMember, UUID> {
}
