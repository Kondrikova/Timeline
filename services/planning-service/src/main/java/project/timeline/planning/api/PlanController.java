package project.timeline.planning.api;

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
import project.timeline.planning.api.dto.PlanningDtos.AllocationResponse;
import project.timeline.planning.api.dto.PlanningDtos.AllocationsRequest;
import project.timeline.planning.api.dto.PlanningDtos.CapacityResponse;
import project.timeline.planning.api.dto.PlanningDtos.ConflictResponse;
import project.timeline.planning.api.dto.PlanningDtos.MoveRequest;
import project.timeline.planning.api.dto.PlanningDtos.MoveResponse;
import project.timeline.planning.service.PlanningService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plan")
public class PlanController {

	private final PlanningService service;

	public PlanController(PlanningService service) {
		this.service = service;
	}

	@GetMapping("/allocations")
	public List<AllocationResponse> allocations() {
		return service.findAll().stream().map(AllocationResponse::of).toList();
	}

	@PutMapping("/tasks/{taskId}/allocations")
	@PreAuthorize("hasRole('ADMIN')")
	public List<AllocationResponse> putAllocations(@PathVariable UUID taskId,
			@Valid @RequestBody AllocationsRequest request) {
		return service.putTaskAllocations(taskId, request.allocations().stream()
						.map(draft -> new PlanningService.AllocationDraft(
								draft.sprintId(), draft.disciplineId(), draft.plannedSp()))
						.toList())
				.stream().map(AllocationResponse::of).toList();
	}

	/**
	 * Перенос с каскадом. При {@code preview=true} изменения не применяются —
	 * возвращается список задач, которые поедут следом.
	 */
	@PostMapping("/tasks/{taskId}/move")
	@PreAuthorize("hasRole('ADMIN')")
	public MoveResponse move(@PathVariable UUID taskId, @Valid @RequestBody MoveRequest request) {
		return MoveResponse.of(service.moveTask(taskId, request.toSprintId(), request.preview()));
	}

	@GetMapping("/capacity")
	public List<CapacityResponse> capacity() {
		return service.capacities().stream().map(CapacityResponse::of).toList();
	}

	@GetMapping("/conflicts")
	public List<ConflictResponse> conflicts() {
		return service.conflicts().stream().map(ConflictResponse::of).toList();
	}

	@PostMapping("/recalculate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	public void recalculate() {
		service.recalculateAll();
	}
}
