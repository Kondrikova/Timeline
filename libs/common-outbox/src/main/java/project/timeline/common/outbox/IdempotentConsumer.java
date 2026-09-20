package project.timeline.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Выполняет обработку события ровно один раз на потребителя.
 *
 * <p>Отметка об обработке пишется в той же транзакции, что и сам эффект: иначе
 * падение между ними привело бы либо к потере эффекта, либо к его дублированию.
 */
@Component
public class IdempotentConsumer {

	private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

	private final ProcessedEventRepository repository;

	public IdempotentConsumer(ProcessedEventRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void runOnce(UUID eventId, String consumer, Runnable action) {
		if (repository.existsByEventIdAndConsumer(eventId, consumer)) {
			log.debug("Событие {} уже обработано потребителем {}, пропускаем", eventId, consumer);
			return;
		}
		action.run();
		repository.save(new ProcessedEvent(eventId, consumer));
	}
}
