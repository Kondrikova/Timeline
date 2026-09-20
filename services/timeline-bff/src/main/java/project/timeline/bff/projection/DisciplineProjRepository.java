package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface DisciplineProjRepository extends MongoRepository<Projections.DisciplineProj, UUID> {
}
