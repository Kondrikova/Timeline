package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface EpicProjRepository extends MongoRepository<Projections.EpicProj, UUID> {
	List<Projections.EpicProj> findAllByOrderByOrderIndexAsc();
}
