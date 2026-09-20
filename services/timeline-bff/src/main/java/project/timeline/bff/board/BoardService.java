package project.timeline.bff.board;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import project.timeline.bff.projection.BoardRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BoardService {

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
		return boards.findById(BoardDocument.CURRENT_ID).orElseGet(() -> {
			BoardDocument empty = assembler.assemble(0);
			return boards.save(empty);
		});
	}

	public BoardDocument rebuild() {
		BoardDocument previous = boards.findById(BoardDocument.CURRENT_ID).orElse(null);
		long revision = previous == null ? 1 : previous.getRevision() + 1;
		BoardDocument board = assembler.assemble(revision);
		BoardDocument saved = boards.save(board);
		lastApplied.set(saved.getUpdatedAt() == null ? Instant.now() : saved.getUpdatedAt());
		hub.publish(saved);
		return saved;
	}
}
