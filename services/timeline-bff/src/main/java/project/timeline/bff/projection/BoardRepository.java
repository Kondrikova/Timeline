package project.timeline.bff.projection;

import org.springframework.data.mongodb.repository.MongoRepository;
import project.timeline.bff.board.BoardDocument;

public interface BoardRepository extends MongoRepository<BoardDocument, String> {
}
