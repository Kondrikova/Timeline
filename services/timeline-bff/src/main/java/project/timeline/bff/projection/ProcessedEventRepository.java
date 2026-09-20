package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<Projections.ProcessedEventDoc, String> {
}
