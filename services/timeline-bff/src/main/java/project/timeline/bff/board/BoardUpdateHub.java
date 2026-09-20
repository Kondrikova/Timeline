package project.timeline.bff.board;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Рассылка уведомлений об обновлении доски через SSE.
 *
 * <p>Пересчёт проекции асинхронный: клиент подписывается и узнаёт о новой
 * ревизии без поллинга.
 */
@Component
public class BoardUpdateHub {

	private static final Logger log = LoggerFactory.getLogger(BoardUpdateHub.class);

	private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(0L);
		subscribers.add(emitter);
		emitter.onCompletion(() -> subscribers.remove(emitter));
		emitter.onTimeout(() -> subscribers.remove(emitter));
		emitter.onError(error -> subscribers.remove(emitter));
		return emitter;
	}

	public void publish(BoardDocument board) {
		Map<String, Object> payload = Map.of(
				"revision", board.getRevision(),
				"updatedAt", board.getUpdatedAt().toString());
		for (SseEmitter emitter : subscribers) {
			try {
				emitter.send(SseEmitter.event()
						.name("board-updated")
						.id(Long.toString(board.getRevision()))
						.data(payload));
			}
			catch (IOException e) {
				emitter.complete();
				subscribers.remove(emitter);
				log.debug("SSE-клиент отключился", e);
			}
		}
	}
}
