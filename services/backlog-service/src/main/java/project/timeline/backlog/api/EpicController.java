package project.timeline.backlog.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.backlog.api.dto.BacklogDtos.EpicRequest;
import project.timeline.backlog.api.dto.BacklogDtos.EpicResponse;
import project.timeline.backlog.api.dto.BacklogDtos.EpicUpdateRequest;
import project.timeline.backlog.service.BacklogService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/epics")
public class EpicController {

	private final BacklogService service;

	public EpicController(BacklogService service) {
		this.service = service;
	}

	@GetMapping
	public List<EpicResponse> list() {
		return service.findEpics().stream().map(EpicResponse::of).toList();
	}

	@GetMapping("/{id}")
	public EpicResponse get(@PathVariable UUID id) {
		return EpicResponse.of(service.requireEpic(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public EpicResponse create(@Valid @RequestBody EpicRequest request) {
		return EpicResponse.of(service.createEpic(request.key(), request.name(),
				request.color(), request.orderIndex()));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public EpicResponse update(@PathVariable UUID id, @Valid @RequestBody EpicUpdateRequest request) {
		return EpicResponse.of(service.updateEpic(id, request.name(), request.color(), request.orderIndex()));
	}
}
