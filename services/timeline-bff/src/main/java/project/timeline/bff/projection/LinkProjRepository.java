package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface LinkProjRepository extends MongoRepository<Projections.LinkProj, UUID> {
}
