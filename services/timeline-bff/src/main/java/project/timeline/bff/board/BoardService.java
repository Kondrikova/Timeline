package project.timeline.bff.board;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import project.timeline.bff.projection.BoardRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BoardService {

	private static final Logger log = LoggerFactory.getLogger(BoardService.class);

	private final BoardRepository boards;
	private final BoardAssembler assembler;
	private final BoardUpdateHub hub;
	private final AtomicReference<Instant> lastApplied = new AtomicReference<>(Instant.EPOCH);

	public BoardService(BoardRepository boards, BoardAssembler assembler, BoardUpdateHub hub,
			MeterRegistry meterRegistry) {
		this.boards = boards;
		this.assembler = assembler;
		this.hub = hub;
		Gauge.builder("board.projection.lag.seconds", lastApplied,
						ref -> {
							Instant at = ref.get();
							if (at.equals(Instant.EPOCH)) {
								return 0;
							}
							return Duration.between(at, Instant.now()).toSeconds();
						})
				.description("Возраст последнего применённого события в проекции доски")
				.register(meterRegistry);
	}

	public BoardDocument current() {
		try {
			BoardDocument existing = readStoredBoard();
			if (existing != null) {
				return existing;
			}
			return rebuild();
		}
		catch (RuntimeException ex) {
			log.error("Не удалось отдать доску, собираем пустую проекцию: {}", ex.getMessage(), ex);
			BoardDocument fallback = assembler.assemble(0);
			try {
				return boards.save(fallback);
			}
			catch (RuntimeException saveError) {
				log.error("Не удалось сохранить fallback доски: {}", saveError.getMessage());
				return fallback;
			}
		}
	}

	public BoardDocument rebuild() {
		long revision = nextRevision();
		BoardDocument board = assembler.assemble(revision);
		BoardDocument saved = boards.save(board);
		lastApplied.set(saved.getUpdatedAt() == null ? Instant.now() : saved.getUpdatedAt());
		hub.publish(saved);
		return saved;
	}

	private long nextRevision() {
		BoardDocument previous = readStoredBoard();
		return previous == null ? 1 : previous.getRevision() + 1;
	}

	/**
	 * Старый документ без новых полей (например {@code workingDays}) Spring Data
	 * может не прочитать. Тогда удаляем его и отдаём null — вызывающий пересоберёт.
	 */
	private BoardDocument readStoredBoard() {
		try {
			return boards.findById(BoardDocument.CURRENT_ID).orElse(null);
		}
		catch (RuntimeException ex) {
			log.warn("Документ доски не читается ({}), пересобираем", ex.getMessage());
			try {
				boards.deleteById(BoardDocument.CURRENT_ID);
			}
			catch (RuntimeException deleteError) {
				log.warn("Не удалось удалить битый документ доски: {}", deleteError.getMessage());
			}
			return null;
		}
	}
}
