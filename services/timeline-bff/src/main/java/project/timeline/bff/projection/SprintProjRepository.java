package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface SprintProjRepository extends MongoRepository<Projections.SprintProj, UUID> {
	List<Projections.SprintProj> findAllByOrderByNumberAsc();
}
