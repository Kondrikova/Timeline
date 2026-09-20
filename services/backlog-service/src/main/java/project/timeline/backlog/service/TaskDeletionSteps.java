package project.timeline.backlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.backlog.domain.SagaInstance;
import project.timeline.backlog.domain.Task;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.backlog.domain.TaskState;
import project.timeline.backlog.repository.SagaInstanceRepository;
import project.timeline.backlog.repository.TaskLinkRepository;
import project.timeline.backlog.repository.TaskRepository;
import project.timeline.common.events.backlog.BacklogEvents;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.security.CurrentUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Шаги саги вынесены в отдельный бин: у каждого своя транзакция. Вызов через
 * {@code this} не прошёл бы через прокси, и компенсация откатилась бы вместе с
 * тем, что должна была исправить.
 */
@Component
public class TaskDeletionSteps {

	private final TaskRepository tasks;
	private final TaskLinkRepository links;
	private final SagaInstanceRepository sagas;
	private final BacklogEventPublisher events;
	private final ObjectMapper objectMapper;

	public TaskDeletionSteps(TaskRepository tasks, TaskLinkRepository links,
			SagaInstanceRepository sagas, BacklogEventPublisher events, ObjectMapper objectMapper) {
		this.tasks = tasks;
		this.links = links;
		this.sagas = sagas;
		this.events = events;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Started begin(UUID taskId) {
		Task task = require(taskId);
		TaskState previousState = task.getState();
		try {
			task.markDeleting();
		}
		catch (IllegalStateException e) {
			throw DomainException.conflict("TASK_DELETE_IN_PROGRESS", e.getMessage());
		}
		SagaInstance saga = sagas.save(new SagaInstance("DELETE_TASK", payload(taskId, previousState)));
		return new Started(saga.getId(), previousState);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void commit(UUID taskId, UUID sagaId) {
		Task task = require(taskId);
		List<TaskLink> touching = links.findAllTouching(taskId);
		String actor = CurrentUser.requireUserId();
		for (TaskLink link : touching) {
			events.linkEvent(BacklogEvents.LINK_REMOVED, link, actor);
		}
		links.deleteAll(touching);
		events.taskEvent(BacklogEvents.TASK_DELETED, task, actor);
		tasks.delete(task);
		sagas.findById(sagaId).ifPresent(SagaInstance::complete);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensate(UUID taskId, UUID sagaId, TaskState previousState, String reason) {
		tasks.findById(taskId).ifPresent(task -> {
			if (previousState == TaskState.ACTIVE) {
				task.restore();
			}
		});
		sagas.findById(sagaId).ifPresent(saga -> saga.compensate(reason));
	}

	private Task require(UUID taskId) {
		return tasks.findById(taskId)
				.orElseThrow(() -> DomainException.notFound("TASK_NOT_FOUND", "Задача не найдена: " + taskId));
	}

	private String payload(UUID taskId, TaskState previousState) {
		try {
			return objectMapper.writeValueAsString(Map.of(
					"taskId", taskId.toString(),
					"previousState", previousState.name()));
		}
		catch (Exception e) {
			throw new IllegalStateException("Не удалось сериализовать состояние саги", e);
		}
	}

	public record Started(UUID sagaId, TaskState previousState) {
	}
}
