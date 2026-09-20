package project.timeline.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.events.schedule.ScheduleEvents;
import project.timeline.common.web.DomainException;
import project.timeline.schedule.domain.SagaInstance;
import project.timeline.schedule.domain.Sprint;
import project.timeline.schedule.domain.SprintState;
import project.timeline.schedule.repository.SagaInstanceRepository;
import project.timeline.schedule.repository.SprintRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Шаги саги вынесены в отдельный бин намеренно: у каждого своя транзакция, а при
 * вызове через {@code this} прокси не сработал бы и все шаги слились бы в одну.
 * Тогда компенсация откатывалась бы вместе с тем, что должна была исправить.
 */
@Component
public class SprintDeletionSteps {

	private final SprintRepository sprints;
	private final SagaInstanceRepository sagas;
	private final ScheduleService schedule;
	private final ObjectMapper objectMapper;

	public SprintDeletionSteps(SprintRepository sprints, SagaInstanceRepository sagas,
			ScheduleService schedule, ObjectMapper objectMapper) {
		this.sprints = sprints;
		this.sagas = sagas;
		this.schedule = schedule;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Started begin(UUID sprintId) {
		Sprint sprint = schedule.require(sprintId);
		SprintState previousState = sprint.getState();
		try {
			sprint.markDeleting();
		}
		catch (IllegalStateException e) {
			throw DomainException.conflict("SPRINT_DELETE_IN_PROGRESS", e.getMessage());
		}
		SagaInstance saga = sagas.save(new SagaInstance("DELETE_SPRINT", payload(sprintId, previousState)));
		return new Started(saga.getId(), previousState);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void commit(UUID sprintId, UUID sagaId) {
		Sprint sprint = schedule.require(sprintId);
		schedule.publish(ScheduleEvents.SPRINT_DELETED, sprint);
		sprints.delete(sprint);
		sagas.findById(sagaId).ifPresent(SagaInstance::complete);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensate(UUID sprintId, UUID sagaId, SprintState previousState, String reason) {
		sprints.findById(sprintId).ifPresent(sprint -> sprint.changeState(previousState));
		sagas.findById(sagaId).ifPresent(saga -> saga.compensate(reason));
	}

	private String payload(UUID sprintId, SprintState previousState) {
		try {
			return objectMapper.writeValueAsString(Map.of(
					"sprintId", sprintId.toString(),
					"previousState", previousState.name()));
		}
		catch (Exception e) {
			throw new IllegalStateException("Не удалось сериализовать состояние саги", e);
		}
	}

	public record Started(UUID sagaId, SprintState previousState) {
	}
}
