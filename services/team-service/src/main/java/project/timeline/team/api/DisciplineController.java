package project.timeline.team.api;

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
import project.timeline.team.api.dto.TeamDtos.DisciplineRequest;
import project.timeline.team.api.dto.TeamDtos.DisciplineResponse;
import project.timeline.team.api.dto.TeamDtos.VelocityRequest;
import project.timeline.team.service.DisciplineService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disciplines")
public class DisciplineController {

	private final DisciplineService service;

	public DisciplineController(DisciplineService service) {
		this.service = service;
	}

	@GetMapping
	public List<DisciplineResponse> list() {
		return service.findAll().stream().map(DisciplineResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public DisciplineResponse create(@Valid @RequestBody DisciplineRequest request) {
		return DisciplineResponse.of(
				service.create(request.code(), request.name(), request.velocitySpPerSprint()));
	}

	@PutMapping("/{id}/velocity")
	@PreAuthorize("hasRole('ADMIN')")
	public DisciplineResponse changeVelocity(@PathVariable UUID id, @Valid @RequestBody VelocityRequest request) {
		return DisciplineResponse.of(service.changeVelocity(id, request.velocitySpPerSprint()));
	}
}
