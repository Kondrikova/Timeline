package project.timeline.bff.board;

import org.springframework.stereotype.Service;
import project.timeline.bff.projection.BoardRepository;

@Service
public class BoardService {

	private final BoardRepository boards;
	private final BoardAssembler assembler;
	private final BoardUpdateHub hub;

	public BoardService(BoardRepository boards, BoardAssembler assembler, BoardUpdateHub hub) {
		this.boards = boards;
		this.assembler = assembler;
		this.hub = hub;
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
		hub.publish(saved);
		return saved;
	}
}
