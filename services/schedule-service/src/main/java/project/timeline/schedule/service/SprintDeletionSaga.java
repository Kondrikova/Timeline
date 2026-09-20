package project.timeline.schedule.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import project.timeline.common.web.DomainException;
import project.timeline.schedule.domain.SagaState;
import project.timeline.schedule.repository.SagaInstanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Удаление спринта — единственная в системе операция, которой действительно нужна
 * распределённая транзакция: на спринт ссылаются аллокации чужого сервиса, а
 * внешним ключом такую связь через границу не защитить.
 *
 * <p>Оркестрация, а не хореография: шагов мало, порядок жёсткий, пользователю
 * нужен синхронный ответ «удалено или нет», а состояние процесса должно быть
 * наблюдаемым.
 */
@Service
public class SprintDeletionSaga {

	private static final Logger log = LoggerFactory.getLogger(SprintDeletionSaga.class);
	static final Duration STUCK_AFTER = Duration.ofMinutes(5);

	private final SprintDeletionSteps steps;
	private final PlanningClient planning;

	public SprintDeletionSaga(SprintDeletionSteps steps, PlanningClient planning,
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
	 * @return сколько задач осталось без плана после удаления спринта
	 */
	public int deleteSprint(UUID sprintId, String authorization) {
		SprintDeletionSteps.Started started = steps.begin(sprintId);
		try {
			int released = planning.releaseAllocations(sprintId, started.sagaId(), authorization);
			steps.commit(sprintId, started.sagaId());
			return released;
		}
		catch (RuntimeException e) {
			log.warn("Шаг саги удаления спринта {} не выполнен, компенсируем", sprintId, e);
			steps.compensate(sprintId, started.sagaId(), started.previousState(), e.getMessage());
			if (e instanceof DomainException domainException) {
				throw domainException;
			}
			throw DomainException.conflict("SPRINT_DELETE_FAILED",
					"Не удалось освободить аллокации спринта, удаление отменено");
		}
	}
}
