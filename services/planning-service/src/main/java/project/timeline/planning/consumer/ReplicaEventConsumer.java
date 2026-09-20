package project.timeline.planning.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.Topics;
import project.timeline.common.events.backlog.BacklogEvents;
import project.timeline.common.events.schedule.ScheduleEvents;
import project.timeline.common.events.team.TeamEvents;
import project.timeline.common.outbox.IdempotentConsumer;
import project.timeline.planning.replica.RefCalendarDay;
import project.timeline.planning.replica.RefDiscipline;
import project.timeline.planning.replica.RefMember;
import project.timeline.planning.replica.RefSprint;
import project.timeline.planning.replica.RefTask;
import project.timeline.planning.replica.RefTaskEstimate;
import project.timeline.planning.replica.RefTaskLink;
import project.timeline.planning.replica.RefVacation;
import project.timeline.planning.repository.AllocationRepository;
import project.timeline.planning.repository.RefCalendarDayRepository;
import project.timeline.planning.repository.RefDisciplineRepository;
import project.timeline.planning.repository.RefMemberRepository;
import project.timeline.planning.repository.RefSprintRepository;
import project.timeline.planning.repository.RefTaskEstimateRepository;
import project.timeline.planning.repository.RefTaskLinkRepository;
import project.timeline.planning.repository.RefTaskRepository;
import project.timeline.planning.repository.RefVacationRepository;
import project.timeline.planning.service.PlanningService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Наполняет локальные реплики по событиям чужих сервисов.
 *
 * <p>Планирование не ходит за данными синхронно: расчёт ёмкости и каскада должен
 * оставаться локальной операцией. Поэтому сюда приезжают копии людей, отпусков,
 * спринтов, календаря, задач, оценок и связей.
 */
@Component
public class ReplicaEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(ReplicaEventConsumer.class);
	private static final String CONSUMER = "planning-replicas";

	private final IdempotentConsumer idempotent;
	private final ObjectMapper objectMapper;
	private final RefDisciplineRepository disciplines;
	private final RefMemberRepository members;
	private final RefVacationRepository vacations;
	private final RefSprintRepository sprints;
	private final RefCalendarDayRepository calendarDays;
	private final RefTaskRepository tasks;
	private final RefTaskEstimateRepository estimates;
	private final RefTaskLinkRepository links;
	private final AllocationRepository allocations;
	private final PlanningService planning;

	public ReplicaEventConsumer(IdempotentConsumer idempotent, ObjectMapper objectMapper,
			RefDisciplineRepository disciplines, RefMemberRepository members, RefVacationRepository vacations,
			RefSprintRepository sprints, RefCalendarDayRepository calendarDays, RefTaskRepository tasks,
			RefTaskEstimateRepository estimates, RefTaskLinkRepository links,
			AllocationRepository allocations, PlanningService planning) {
		this.idempotent = idempotent;
		this.objectMapper = objectMapper;
		this.disciplines = disciplines;
		this.members = members;
		this.vacations = vacations;
		this.sprints = sprints;
		this.calendarDays = calendarDays;
		this.tasks = tasks;
		this.estimates = estimates;
		this.links = links;
		this.allocations = allocations;
		this.planning = planning;
	}

	@KafkaListener(topics = Topics.TEAM_MEMBER, groupId = "planning-service")
	@Transactional
	public void onTeamMember(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			if (TeamEvents.DISCIPLINE_UPDATED.equals(type)) {
				upsertDiscipline(payload);
			}
			else {
				upsertMember(payload);
			}
			planning.recalculateAll();
		});
	}

	@KafkaListener(topics = Topics.TEAM_VACATION, groupId = "planning-service")
	@Transactional
	public void onVacation(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			UUID vacationId = uuid(payload, "vacationId");
			if (TeamEvents.VACATION_CANCELLED.equals(type)) {
				vacations.deleteById(vacationId);
			}
			else {
				vacations.save(new RefVacation(vacationId, uuid(payload, "memberId"),
						date(payload, "startDate"), date(payload, "endDate")));
			}
			planning.recalculateAll();
		});
	}

	@KafkaListener(topics = Topics.SCHEDULE_SPRINT, groupId = "planning-service")
	@Transactional
	public void onSprint(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			UUID sprintId = uuid(payload, "sprintId");
			if (ScheduleEvents.SPRINT_DELETED.equals(type)) {
				sprints.deleteById(sprintId);
			}
			else {
				sprints.save(new RefSprint(sprintId, payload.get("number").asInt(),
						text(payload, "name"), date(payload, "startDate"), date(payload, "endDate"),
						text(payload, "state")));
			}
			planning.recalculateAll();
		});
	}

	@KafkaListener(topics = Topics.SCHEDULE_CALENDAR, groupId = "planning-service")
	@Transactional
	public void onCalendar(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			JsonNode days = event.get("payload").get("days");
			if (days != null && days.isArray()) {
				for (JsonNode day : days) {
					calendarDays.save(new RefCalendarDay(date(day, "day"), day.get("working").asBoolean()));
				}
			}
			planning.recalculateAll();
		});
	}

	@KafkaListener(topics = Topics.BACKLOG_TASK, groupId = "planning-service")
	@Transactional
	public void onTask(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			UUID taskId = uuid(payload, "taskId");
			if (BacklogEvents.TASK_DELETED.equals(type)) {
				estimates.deleteAllByTaskId(taskId);
				allocations.deleteAllByTaskId(taskId);
				tasks.deleteById(taskId);
			}
			else {
				tasks.save(new RefTask(taskId, text(payload, "key"), text(payload, "title"),
						payload.hasNonNull("epicId") ? uuid(payload, "epicId") : null,
						text(payload, "status")));
				replaceEstimates(taskId, payload.get("estimates"));
			}
			planning.recalculateAll();
		});
	}

	@KafkaListener(topics = Topics.BACKLOG_LINK, groupId = "planning-service")
	@Transactional
	public void onLink(String json) {
		handle(json, () -> {
			JsonNode event = read(json);
			String type = text(event, "eventType");
			JsonNode payload = event.get("payload");
			UUID linkId = uuid(payload, "linkId");
			if (BacklogEvents.LINK_REMOVED.equals(type)) {
				links.deleteById(linkId);
			}
			else {
				links.save(new RefTaskLink(linkId, uuid(payload, "fromTaskId"), uuid(payload, "toTaskId"),
						text(payload, "type"), text(payload, "hardness")));
			}
			planning.recalculateAll();
		});
	}

	private void upsertDiscipline(JsonNode payload) {
		disciplines.save(new RefDiscipline(uuid(payload, "disciplineId"), text(payload, "code"),
				text(payload, "name"), decimal(payload, "velocitySpPerSprint")));
	}

	private void upsertMember(JsonNode payload) {
		members.save(new RefMember(uuid(payload, "memberId"), uuid(payload, "disciplineId"),
				text(payload, "fullName"), date(payload, "activeFrom"),
				payload.hasNonNull("activeTo") ? date(payload, "activeTo") : null));
	}

	private void replaceEstimates(UUID taskId, JsonNode estimatesNode) {
		estimates.deleteAllByTaskId(taskId);
		if (estimatesNode == null || !estimatesNode.isArray()) {
			return;
		}
		for (JsonNode estimate : estimatesNode) {
			BigDecimal sp = estimate.hasNonNull("estimateSp")
					? decimal(estimate, "estimateSp") : null;
			estimates.save(new RefTaskEstimate(taskId, uuid(estimate, "disciplineId"), sp));
		}
	}

	private void handle(String json, Runnable action) {
		try {
			JsonNode event = read(json);
			UUID eventId = UUID.fromString(text(event, "eventId"));
			idempotent.runOnce(eventId, CONSUMER, action);
		}
		catch (Exception e) {
			log.error("Не удалось применить событие: {}", json, e);
			throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
		}
	}

	private JsonNode read(String json) {
		try {
			return objectMapper.readTree(json);
		}
		catch (Exception e) {
			throw new IllegalStateException("Некорректный JSON события", e);
		}
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

	private static BigDecimal decimal(JsonNode node, String field) {
		return new BigDecimal(node.get(field).asText());
	}
}
