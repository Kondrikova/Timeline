package project.timeline.backlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.backlog.domain.Epic;

import java.util.List;
import java.util.UUID;

public interface EpicRepository extends JpaRepository<Epic, UUID> {

	List<Epic> findAllByOrderByOrderIndexAsc();

	boolean existsByKey(String key);
}
