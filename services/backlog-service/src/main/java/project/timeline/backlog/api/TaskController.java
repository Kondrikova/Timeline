package project.timeline.backlog.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.backlog.api.dto.BacklogDtos.CommentRequest;
import project.timeline.backlog.api.dto.BacklogDtos.CommentResponse;
import project.timeline.backlog.api.dto.BacklogDtos.EstimatesRequest;
import project.timeline.backlog.api.dto.BacklogDtos.LinkRequest;
import project.timeline.backlog.api.dto.BacklogDtos.LinkResponse;
import project.timeline.backlog.api.dto.BacklogDtos.StatusRequest;
import project.timeline.backlog.api.dto.BacklogDtos.TaskDeletedResponse;
import project.timeline.backlog.api.dto.BacklogDtos.TaskRequest;
import project.timeline.backlog.api.dto.BacklogDtos.TaskResponse;
import project.timeline.backlog.api.dto.BacklogDtos.TaskUpdateRequest;
import project.timeline.backlog.domain.LinkHardness;
import project.timeline.backlog.domain.TaskStatus;
import project.timeline.backlog.service.BacklogService;
import project.timeline.backlog.service.TaskDeletionSaga;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

	private final BacklogService service;
	private final TaskDeletionSaga deletionSaga;

	public TaskController(BacklogService service, TaskDeletionSaga deletionSaga) {
		this.service = service;
		this.deletionSaga = deletionSaga;
	}

	@GetMapping
	public List<TaskResponse> list(
			@RequestParam(required = false) UUID epicId,
			@RequestParam(required = false) TaskStatus status) {
		return service.findTasks(epicId, status).stream().map(TaskResponse::of).toList();
	}

	@GetMapping("/{id}")
	public TaskResponse get(@PathVariable UUID id) {
		return TaskResponse.of(service.requireTask(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public TaskResponse create(@Valid @RequestBody TaskRequest request) {
		return TaskResponse.of(service.createTask(request.key(), request.title(),
				request.epicId(), request.priority(), request.description()));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskUpdateRequest request) {
		return TaskResponse.of(service.updateTask(id, request.title(), request.epicId(),
				request.priority(), request.description()));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public TaskResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		return TaskResponse.of(service.changeStatus(id, request.status()));
	}

	@PutMapping("/{id}/estimates")
	@PreAuthorize("hasRole('ADMIN')")
	public TaskResponse estimates(@PathVariable UUID id, @Valid @RequestBody EstimatesRequest request) {
		return TaskResponse.of(service.replaceEstimates(id, request.estimates()));
	}

	/**
	 * Удаление задачи — сага: сначала снимаются аллокации в planning, затем
	 * задача и связи. Ответ сообщает, сколько аллокаций было снято.
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public TaskDeletedResponse delete(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
		return new TaskDeletedResponse(id, deletionSaga.deleteTask(id, authorization));
	}

	@GetMapping("/{id}/links")
	public List<LinkResponse> links(@PathVariable UUID id) {
		return service.findLinks(id).stream().map(LinkResponse::of).toList();
	}

	@PostMapping("/{id}/links")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public LinkResponse addLink(@PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
		LinkHardness hardness = request.hardness() == null ? LinkHardness.HARD : request.hardness();
		return LinkResponse.of(service.addLink(id, request.toTaskId(), request.type(), hardness));
	}

	@DeleteMapping("/{id}/links/{linkId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	public void removeLink(@PathVariable UUID id, @PathVariable UUID linkId) {
		service.removeLink(id, linkId);
	}

	@GetMapping("/{id}/comments")
	public List<CommentResponse> comments(@PathVariable UUID id) {
		return service.findComments(id).stream().map(CommentResponse::of).toList();
	}

	@PostMapping("/{id}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse addComment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
		return CommentResponse.of(service.addComment(id, request.body()));
	}
}
