package project.timeline.backlog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.backlog.domain.Epic;
import project.timeline.backlog.domain.LinkHardness;
import project.timeline.backlog.domain.LinkType;
import project.timeline.backlog.domain.Task;
import project.timeline.backlog.domain.TaskComment;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.backlog.domain.TaskSource;
import project.timeline.backlog.domain.TaskStatus;
import project.timeline.backlog.repository.EpicRepository;
import project.timeline.backlog.repository.TaskCommentRepository;
import project.timeline.backlog.repository.TaskLinkRepository;
import project.timeline.backlog.repository.TaskRepository;
import project.timeline.common.events.backlog.BacklogEvents;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.security.CurrentUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BacklogService {

	private final EpicRepository epics;
	private final TaskRepository tasks;
	private final TaskLinkRepository links;
	private final TaskCommentRepository comments;
	private final TaskLinkValidator linkValidator;
	private final BacklogEventPublisher events;

	public BacklogService(EpicRepository epics, TaskRepository tasks, TaskLinkRepository links,
			TaskCommentRepository comments, TaskLinkValidator linkValidator, BacklogEventPublisher events) {
		this.epics = epics;
		this.tasks = tasks;
		this.links = links;
		this.comments = comments;
		this.linkValidator = linkValidator;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<Epic> findEpics() {
		return epics.findAllByOrderByOrderIndexAsc();
	}

	@Transactional
	public Epic createEpic(String key, String name, String color, int orderIndex) {
		if (epics.existsByKey(key)) {
			throw DomainException.conflict("EPIC_EXISTS", "Эпик с ключом " + key + " уже существует");
		}
		return epics.save(new Epic(key, name, color, orderIndex));
	}

	@Transactional(readOnly = true)
	public List<Task> findTasks(UUID epicId, TaskStatus status) {
		if (epicId != null && status != null) {
			return tasks.findAllByEpicIdAndStatusOrderByPriorityAscKeyAsc(epicId, status);
		}
		if (epicId != null) {
			return tasks.findAllByEpicIdOrderByPriorityAscKeyAsc(epicId);
		}
		if (status != null) {
			return tasks.findAllByStatusOrderByPriorityAscKeyAsc(status);
		}
		return tasks.findAllByOrderByPriorityAscKeyAsc();
	}

	@Transactional(readOnly = true)
	public Task requireTask(UUID id) {
		return tasks.findById(id)
				.orElseThrow(() -> DomainException.notFound("TASK_NOT_FOUND", "Задача не найдена: " + id));
	}

	@Transactional
	public Task createTask(String key, String title, UUID epicId, int priority, String description) {
		if (tasks.existsByKey(key)) {
			throw DomainException.conflict("TASK_EXISTS", "Задача с ключом " + key + " уже существует");
		}
		requireEpicExists(epicId);
		Task task = tasks.save(new Task(key, title, epicId, priority, description, TaskSource.LOCAL));
		events.taskEvent(BacklogEvents.TASK_CREATED, task, CurrentUser.requireUserId());
		return task;
	}

	@Transactional
	public Task updateTask(UUID id, String title, UUID epicId, int priority, String description) {
		Task task = requireTask(id);
		requireEpicExists(epicId);
		task.update(title, epicId, priority, description);
		events.taskEvent(BacklogEvents.TASK_UPDATED, task, CurrentUser.requireUserId());
		return task;
	}

	@Transactional
	public Task changeStatus(UUID id, TaskStatus status) {
		Task task = requireTask(id);
		task.changeStatus(status);
		events.taskEvent(BacklogEvents.TASK_STATUS_CHANGED, task, CurrentUser.requireUserId());
		return task;
	}

	/**
	 * Замена оценок целиком задаёт и перечень задействованных ролей: роли, которой
	 * нет в новом наборе, в задаче больше нет.
	 */
	@Transactional
	public Task replaceEstimates(UUID id, Map<UUID, BigDecimal> estimates) {
		Task task = requireTask(id);
		try {
			task.replaceEstimates(estimates);
		}
		catch (IllegalArgumentException e) {
			throw DomainException.badRequest("ESTIMATE_INVALID", e.getMessage());
		}
		events.taskEvent(BacklogEvents.TASK_ESTIMATED, task, CurrentUser.requireUserId());
		return task;
	}

	@Transactional(readOnly = true)
	public List<TaskLink> findLinks(UUID taskId) {
		return links.findAllTouching(taskId);
	}

	@Transactional
	public TaskLink addLink(UUID fromTaskId, UUID toTaskId, LinkType type, LinkHardness hardness) {
		requireTask(fromTaskId);
		requireTask(toTaskId);

		TaskLink candidate;
		try {
			candidate = new TaskLink(fromTaskId, toTaskId, type, hardness);
		}
		catch (IllegalArgumentException e) {
			throw DomainException.badRequest("LINK_INVALID", e.getMessage());
		}

		// Проверка охватывает всю связную компоненту графа, поэтому выполняется на
		// полном наборе связей в той же транзакции, что и вставка.
		linkValidator.validate(links.findAll(), candidate);

		TaskLink saved = links.save(candidate);
		events.linkEvent(BacklogEvents.LINK_ADDED, saved, CurrentUser.requireUserId());
		return saved;
	}

	@Transactional
	public void removeLink(UUID taskId, UUID linkId) {
		TaskLink link = links.findById(linkId)
				.orElseThrow(() -> DomainException.notFound("LINK_NOT_FOUND", "Связь не найдена: " + linkId));
		if (!link.touches(taskId)) {
			throw DomainException.badRequest("LINK_NOT_RELATED", "Связь не относится к этой задаче");
		}
		links.delete(link);
		events.linkEvent(BacklogEvents.LINK_REMOVED, link, CurrentUser.requireUserId());
	}

	@Transactional(readOnly = true)
	public List<TaskComment> findComments(UUID taskId) {
		return comments.findAllByTaskIdOrderByCreatedAtAsc(taskId);
	}

	@Transactional
	public TaskComment addComment(UUID taskId, String body) {
		requireTask(taskId);
		return comments.save(new TaskComment(taskId, CurrentUser.requireUserId(), body));
	}

	@Transactional(readOnly = true)
	public long countComments(UUID taskId) {
		return comments.countByTaskId(taskId);
	}

	private void requireEpicExists(UUID epicId) {
		Optional.ofNullable(epicId).ifPresent(id -> {
			if (!epics.existsById(id)) {
				throw DomainException.notFound("EPIC_NOT_FOUND", "Эпик не найден: " + id);
			}
		});
	}
}
