package project.timeline.bff.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import project.timeline.bff.board.BoardService;
import project.timeline.bff.projection.DisciplineProjRepository;
import project.timeline.bff.projection.EpicProjRepository;
import project.timeline.bff.projection.LinkProjRepository;
import project.timeline.bff.projection.PlanProjRepository;
import project.timeline.bff.projection.ProcessedEventRepository;
import project.timeline.bff.projection.Projections;
import project.timeline.bff.projection.SprintProjRepository;
import project.timeline.bff.projection.TaskProjRepository;
import project.timeline.common.events.Topics;
import project.timeline.common.events.backlog.BacklogEvents;
import project.timeline.common.events.planning.PlanningEvents;
import project.timeline.common.events.schedule.ScheduleEvents;
import project.timeline.common.events.team.TeamEvents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class BoardProjectionConsumer {

	private static final Logger log = LoggerFactory.getLogger(BoardProjectionConsumer.class);

	private final ObjectMapper objectMapper;
	private final ProcessedEventRepository processed;
	private final SprintProjRepository sprints;
	private final EpicProjRepository epics;
	private final TaskProjRepository tasks;
	private final LinkProjRepository links;
	private final DisciplineProjRepository disciplines;
	private final PlanProjRepository plans;
	private final BoardService boards;
	private final Counter dltCounter;

	public BoardProjectionConsumer(ObjectMapper objectMapper, ProcessedEventRepository processed,
			SprintProjRepository sprints, EpicProjRepository epics, TaskProjRepository tasks,
			LinkProjRepository links, DisciplineProjRepository disciplines, PlanProjRepository plans,
			BoardService boards, MeterRegistry meterRegistry) {
		this.objectMapper = objectMapper;
		this.processed = processed;
		this.sprints = sprints;
		this.epics = epics;
		this.tasks = tasks;
		this.links = links;
		this.disciplines = disciplines;
		this.plans = plans;
		this.boards = boards;
		this.dltCounter = Counter.builder("kafka.dlt.messages")
				.description("Сообщения, ушедшие в dead-letter topic")
				.register(meterRegistry);
	}

	@RetryableTopic(
			attempts = "4",
			backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
			dltStrategy = DltStrategy.FAIL_ON_ERROR,
			topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
			retryTopicSuffix = ".retry",
			dltTopicSuffix = ".dlt",
			exclude = {ConversionException.class, MessageConversionException.class,
					IllegalArgumentException.class})
	@KafkaListener(topics = {
			Topics.TEAM_MEMBER,
			Topics.SCHEDULE_SPRINT,
			Topics.BACKLOG_EPIC,
			Topics.BACKLOG_TASK,
			Topics.BACKLOG_LINK,
			Topics.PLANNING_ALLOCATION
	}, groupId = "timeline-bff")
	public void onEvent(String json) {
		try {
			JsonNode event = objectMapper.readTree(json);
			String eventId = text(event, "eventId");
			if (processed.existsById(eventId)) {
				return;
			}
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			apply(type, payload);
			processed.save(new Projections.ProcessedEventDoc(eventId));
			boards.rebuild();
		}
		catch (Exception e) {
			log.error("Не удалось обновить проекцию доски: {}", json, e);
			throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
		}
	}

	@DltHandler
	public void onDlt(String payload,
			@Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
		dltCounter.increment();
		log.error("Сообщение ушло в DLT топика {}: {}", topic, payload);
	}

	private void apply(String type, JsonNode payload) {
		switch (type) {
			case TeamEvents.DISCIPLINE_UPDATED -> {
				Projections.DisciplineProj discipline = new Projections.DisciplineProj();
				discipline.id = uuid(payload, "disciplineId");
				discipline.code = text(payload, "code");
				discipline.name = text(payload, "name");
				disciplines.save(discipline);
			}
			case ScheduleEvents.SPRINT_CREATED, ScheduleEvents.SPRINT_RESCHEDULED -> {
				Projections.SprintProj sprint = new Projections.SprintProj();
				sprint.id = uuid(payload, "sprintId");
				sprint.number = payload.get("number").asInt();
				sprint.name = text(payload, "name");
				sprint.startDate = date(payload, "startDate");
				sprint.endDate = date(payload, "endDate");
				sprint.state = text(payload, "state");
				sprints.save(sprint);
			}
			case ScheduleEvents.SPRINT_DELETED -> sprints.deleteById(uuid(payload, "sprintId"));
			case "EpicCreated", "EpicUpdated" -> {
				Projections.EpicProj epic = new Projections.EpicProj();
				epic.id = uuid(payload, "epicId");
				epic.key = text(payload, "key");
				epic.name = text(payload, "name");
				epic.color = payload.hasNonNull("color") ? text(payload, "color") : null;
				epic.orderIndex = payload.path("orderIndex").asInt(0);
				epics.save(epic);
			}
			case BacklogEvents.TASK_CREATED, BacklogEvents.TASK_UPDATED,
					BacklogEvents.TASK_ESTIMATED, BacklogEvents.TASK_STATUS_CHANGED -> saveTask(payload);
			case BacklogEvents.TASK_DELETED -> {
				UUID taskId = uuid(payload, "taskId");
				links.deleteAllByFromTaskIdOrToTaskId(taskId, taskId);
				tasks.deleteById(taskId);
			}
			case BacklogEvents.LINK_ADDED -> {
				Projections.LinkProj link = new Projections.LinkProj();
				link.id = uuid(payload, "linkId");
				link.fromTaskId = uuid(payload, "fromTaskId");
				link.toTaskId = uuid(payload, "toTaskId");
				link.type = text(payload, "type");
				link.hardness = text(payload, "hardness");
				links.save(link);
			}
			case BacklogEvents.LINK_REMOVED -> links.deleteById(uuid(payload, "linkId"));
			case PlanningEvents.PLAN_RECALCULATED -> savePlan(payload);
			default -> log.debug("Событие {} для доски несущественно, пропуск", type);
		}
	}

	private void saveTask(JsonNode payload) {
		Projections.TaskProj task = new Projections.TaskProj();
		task.id = uuid(payload, "taskId");
		task.key = text(payload, "key");
		task.title = text(payload, "title");
		task.epicId = payload.hasNonNull("epicId") ? uuid(payload, "epicId") : null;
		task.status = text(payload, "status");
		task.priority = payload.path("priority").asInt(0);
		task.estimates = new ArrayList<>();
		JsonNode estimates = payload.get("estimates");
		if (estimates != null && estimates.isArray()) {
			for (JsonNode estimate : estimates) {
				Projections.Estimate item = new Projections.Estimate();
				item.disciplineId = uuid(estimate, "disciplineId");
				item.estimateSp = estimate.hasNonNull("estimateSp")
						? new BigDecimal(estimate.get("estimateSp").asText()) : null;
				task.estimates.add(item);
			}
		}
		tasks.save(task);
	}

	private void savePlan(JsonNode payload) {
		Projections.PlanProj plan = new Projections.PlanProj();
		plan.planVersionId = uuid(payload, "planVersionId");
		plan.allocations = new ArrayList<>();
		plan.capacities = new ArrayList<>();
		plan.conflicts = new ArrayList<>();

		for (JsonNode node : payload.withArray("allocations")) {
			Projections.Allocation allocation = new Projections.Allocation();
			allocation.taskId = uuid(node, "taskId");
			allocation.sprintId = uuid(node, "sprintId");
			allocation.disciplineId = uuid(node, "disciplineId");
			allocation.plannedSp = new BigDecimal(node.get("plannedSp").asText());
			plan.allocations.add(allocation);
		}
		for (JsonNode node : payload.withArray("capacities")) {
			Projections.Capacity capacity = new Projections.Capacity();
			capacity.sprintId = uuid(node, "sprintId");
			capacity.disciplineId = uuid(node, "disciplineId");
			capacity.availablePersonDays = node.get("availablePersonDays").asInt();
			capacity.vacationPersonDays = node.get("vacationPersonDays").asInt();
			capacity.capacitySp = new BigDecimal(node.get("capacitySp").asText());
			capacity.allocatedSp = new BigDecimal(node.get("allocatedSp").asText());
			plan.capacities.add(capacity);
		}
		for (JsonNode node : payload.withArray("conflicts")) {
			Projections.Conflict conflict = new Projections.Conflict();
			conflict.id = uuid(node, "id");
			conflict.type = text(node, "type");
			conflict.severity = text(node, "severity");
			conflict.sprintId = node.hasNonNull("sprintId") ? uuid(node, "sprintId") : null;
			conflict.disciplineId = node.hasNonNull("disciplineId") ? uuid(node, "disciplineId") : null;
			conflict.taskId = node.hasNonNull("taskId") ? uuid(node, "taskId") : null;
			conflict.details = text(node, "details");
			plan.conflicts.add(conflict);
		}
		plans.save(plan);
	}

	private static String text(JsonNode node, String field) {
		return node.get(field).asText();
	}

	private static UUID uuid(JsonNode node, String field) {
		return UUID.fromString(node.get(field).asText());
	}

	private static LocalDate date(JsonNode node, String field) {
		return LocalDate.parse(node.get(field).asText());
	}
}
