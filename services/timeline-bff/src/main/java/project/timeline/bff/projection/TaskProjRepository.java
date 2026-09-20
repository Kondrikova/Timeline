package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface TaskProjRepository extends MongoRepository<Projections.TaskProj, UUID> {
	List<Projections.TaskProj> findAllByOrderByPriorityAscKeyAsc();
}
