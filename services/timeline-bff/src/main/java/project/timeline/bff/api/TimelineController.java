package project.timeline.bff.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import project.timeline.bff.board.BoardDocument;
import project.timeline.bff.board.BoardService;
import project.timeline.bff.board.BoardUpdateHub;

@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {

	private final BoardService boards;
	private final BoardUpdateHub hub;

	public TimelineController(BoardService boards, BoardUpdateHub hub) {
		this.boards = boards;
		this.hub = hub;
	}

	/**
	 * Один запрос — полная доска. {@code ETag} по ревизии позволяет клиенту
	 * получать {@code 304}, если проекция не изменилась.
	 */
	@GetMapping("/board")
	public ResponseEntity<BoardDocument> board(
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		BoardDocument board = boards.current();
		String etag = "\"" + board.getRevision() + "\"";
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(304).eTag(etag).cacheControl(CacheControl.noCache()).build();
		}
		return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(board);
	}

	@GetMapping("/stream")
	public SseEmitter stream() {
		return hub.subscribe();
	}
}
