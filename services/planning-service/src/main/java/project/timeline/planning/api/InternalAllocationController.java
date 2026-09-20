package project.timeline.planning.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseRequest;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseResponse;
import project.timeline.planning.service.PlanningService;

/**
 * Внутренний API для шага саги удаления спринта.
 *
 * <p>Не выставляется через gateway наружу: вызывается только schedule-service.
 * Операция идемпотентна — повторный вызов на уже пустом спринте возвращает ноль.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalAllocationController {

	private final PlanningService service;

	public InternalAllocationController(PlanningService service) {
		this.service = service;
	}

	@PostMapping("/allocations/release")
	@PreAuthorize("hasRole('ADMIN')")
	public ReleaseResponse release(@Valid @RequestBody ReleaseRequest request) {
		return new ReleaseResponse(service.releaseSprintAllocations(request.sprintId()));
	}
}
