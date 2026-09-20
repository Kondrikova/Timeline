package project.timeline.backlog.service;

import org.springframework.stereotype.Component;
import project.timeline.backlog.domain.Epic;
import project.timeline.backlog.domain.Task;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.common.events.DomainEvent;
import project.timeline.common.events.Topics;
import project.timeline.common.events.backlog.BacklogEpicEvents;
import project.timeline.common.events.backlog.BacklogEvents;
import project.timeline.common.outbox.OutboxPublisher;
import project.timeline.common.web.RequestCorrelationFilter;

@Component
public class BacklogEventPublisher {

	private final OutboxPublisher outbox;

	public BacklogEventPublisher(OutboxPublisher outbox) {
		this.outbox = outbox;
	}

	public void epicEvent(String eventType, Epic epic, String actorId) {
		outbox.publish(Topics.BACKLOG_EPIC, DomainEvent.of(
				eventType,
				"Epic",
				epic.getId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				new BacklogEpicEvents.EpicPayload(
						epic.getId(), epic.getKey(), epic.getName(), epic.getColor(), epic.getOrderIndex())));
	}

	public void taskEvent(String eventType, Task task, String actorId) {
		outbox.publish(Topics.BACKLOG_TASK, DomainEvent.of(
				eventType,
				"Task",
				task.getId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				new BacklogEvents.TaskPayload(
						task.getId(),
						task.getKey(),
						task.getTitle(),
						task.getEpicId(),
						task.getStatus().name(),
						task.getPriority(),
						task.getEstimates().entrySet().stream()
								.map(entry -> new BacklogEvents.EstimatePayload(entry.getKey(), entry.getValue()))
								.toList())));
	}

	/**
	 * Ключом партиции служит задача-источник: связь принадлежит обеим сторонам, но
	 * порядок важно сохранить хотя бы относительно одной из них.
	 */
	public void linkEvent(String eventType, TaskLink link, String actorId) {
		outbox.publish(Topics.BACKLOG_LINK, DomainEvent.of(
				eventType,
				"Task",
				link.getFromTaskId(),
				actorId,
				RequestCorrelationFilter.currentTraceId(),
				new BacklogEvents.LinkPayload(
						link.getId(),
						link.getFromTaskId(),
						link.getToTaskId(),
						link.getType().name(),
						link.getHardness().name())));
	}
}
