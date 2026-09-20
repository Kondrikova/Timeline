package project.timeline.planning.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseRequest;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseResponse;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseTaskRequest;
import project.timeline.planning.api.dto.PlanningDtos.ReleaseTaskResponse;
import project.timeline.planning.service.PlanningService;

/**
 * Внутренний API для шагов саг удаления спринта и задачи.
 *
 * <p>Не выставляется через gateway наружу: вызывается только schedule-service и
 * backlog-service. Операции идемпотентны — повторный вызов на уже пустых данных
 * возвращает ноль.
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

	@PostMapping("/allocations/release-task")
	@PreAuthorize("hasRole('ADMIN')")
	public ReleaseTaskResponse releaseTask(@Valid @RequestBody ReleaseTaskRequest request) {
		return new ReleaseTaskResponse(service.releaseTaskAllocations(request.taskId()));
	}
}
