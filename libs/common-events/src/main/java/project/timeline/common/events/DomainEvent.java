package project.timeline.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Конверт доменного события, единый для всех сервисов.
 *
 * <p>{@code aggregateId} служит ключом партиции Kafka: это гарантирует, что события
 * одного агрегата попадают в одну партицию и применяются потребителями в порядке
 * возникновения. {@code traceId} обязателен — без него асинхронная цепочка
 * обрывается на границе брокера и перестаёт прослеживаться.
 *
 * @param payload тело события; сериализуется как есть
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEvent<T>(
		UUID eventId,
		String eventType,
		int version,
		Instant occurredAt,
		String aggregateType,
		UUID aggregateId,
		String actorId,
		String traceId,
		T payload) {

	public static <T> DomainEvent<T> of(
			String eventType,
			String aggregateType,
			UUID aggregateId,
			String actorId,
			String traceId,
			T payload) {
		return new DomainEvent<>(
				UUID.randomUUID(),
				eventType,
				1,
				Instant.now(),
				aggregateType,
				aggregateId,
				actorId,
				traceId,
				payload);
	}
}
