package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlanProjRepository extends MongoRepository<Projections.PlanProj, String> {
}
