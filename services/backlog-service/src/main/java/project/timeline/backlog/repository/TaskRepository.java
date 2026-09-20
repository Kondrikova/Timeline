package project.timeline.backlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.backlog.domain.Task;
import project.timeline.backlog.domain.TaskStatus;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	boolean existsByKey(String key);

	List<Task> findAllByOrderByPriorityAscKeyAsc();

	List<Task> findAllByStatusOrderByPriorityAscKeyAsc(TaskStatus status);

	List<Task> findAllByEpicIdOrderByPriorityAscKeyAsc(UUID epicId);

	List<Task> findAllByEpicIdAndStatusOrderByPriorityAscKeyAsc(UUID epicId, TaskStatus status);
}
