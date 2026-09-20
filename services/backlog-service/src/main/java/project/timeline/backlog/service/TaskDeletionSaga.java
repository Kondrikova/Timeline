package project.timeline.backlog.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import project.timeline.backlog.domain.SagaState;
import project.timeline.backlog.repository.SagaInstanceRepository;
import project.timeline.common.web.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Оркестрация удаления задачи: снять аллокации в planning, затем удалить задачу
 * и связи. Компенсация возвращает задачу из {@code DELETING} в {@code ACTIVE}.
 */
@Service
public class TaskDeletionSaga {

	private static final Logger log = LoggerFactory.getLogger(TaskDeletionSaga.class);
	static final Duration STUCK_AFTER = Duration.ofMinutes(5);

	private final TaskDeletionSteps steps;
	private final PlanningClient planning;

	public TaskDeletionSaga(TaskDeletionSteps steps, PlanningClient planning,
			SagaInstanceRepository sagas, MeterRegistry meterRegistry) {
		this.steps = steps;
		this.planning = planning;

		Gauge.builder("saga.stuck", sagas,
						repository -> repository.countByStateAndStartedAtBefore(
								SagaState.STARTED, Instant.now().minus(STUCK_AFTER)))
				.description("Саги, не завершившиеся за пять минут")
				.register(meterRegistry);
	}

	/**
	 * @return сколько аллокаций снято перед удалением задачи
	 */
	public int deleteTask(UUID taskId, String authorization) {
		TaskDeletionSteps.Started started = steps.begin(taskId);
		try {
			int released = planning.releaseTaskAllocations(taskId, started.sagaId(), authorization);
			steps.commit(taskId, started.sagaId());
			return released;
		}
		catch (RuntimeException e) {
			log.warn("Шаг саги удаления задачи {} не выполнен, компенсируем", taskId, e);
			steps.compensate(taskId, started.sagaId(), started.previousState(), e.getMessage());
			if (e instanceof DomainException domainException) {
				throw domainException;
			}
			throw DomainException.conflict("TASK_DELETE_FAILED",
					"Не удалось освободить аллокации задачи, удаление отменено");
		}
	}
}
