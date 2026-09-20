package project.timeline.planning.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import project.timeline.planning.domain.Allocation;
import project.timeline.planning.domain.ConflictType;
import project.timeline.planning.domain.PlanConflict;
import project.timeline.planning.replica.SprintCapacity;
import project.timeline.planning.service.PlanningService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlanningDtos {

	public record AllocationDraft(
			@NotNull UUID sprintId,
			@NotNull UUID disciplineId,
			@NotNull BigDecimal plannedSp) {
	}

	public record AllocationsRequest(@NotNull @Valid List<AllocationDraft> allocations) {
	}

	public record AllocationResponse(
			UUID id,
			UUID taskId,
			UUID sprintId,
			UUID disciplineId,
			BigDecimal plannedSp) {

		public static AllocationResponse of(Allocation allocation) {
			return new AllocationResponse(allocation.getId(), allocation.getTaskId(),
					allocation.getSprintId(), allocation.getDisciplineId(), allocation.getPlannedSp());
		}
	}

	public record MoveRequest(@NotNull UUID toSprintId, boolean preview) {
	}

	public record MoveResponse(boolean preview, Map<UUID, Integer> shifts, List<ConflictResponse> conflicts) {

		public static MoveResponse of(PlanningService.MoveResult result) {
			return new MoveResponse(result.preview(), result.shiftedTasks(),
					result.conflicts().stream().map(ConflictResponse::of).toList());
		}
	}

	public record CapacityResponse(
			UUID sprintId,
			UUID disciplineId,
			int availablePersonDays,
			int vacationPersonDays,
			BigDecimal capacitySp) {

		public static CapacityResponse of(SprintCapacity capacity) {
			return new CapacityResponse(capacity.getSprintId(), capacity.getDisciplineId(),
					capacity.getAvailablePersonDays(), capacity.getVacationPersonDays(),
					capacity.getCapacitySp());
		}
	}

	public record ConflictResponse(
			UUID id,
			ConflictType type,
			ConflictType.Severity severity,
			UUID sprintId,
			UUID disciplineId,
			UUID taskId,
			String details,
			Instant detectedAt) {

		public static ConflictResponse of(PlanConflict conflict) {
			return new ConflictResponse(conflict.getId(), conflict.getType(), conflict.getSeverity(),
					conflict.getSprintId(), conflict.getDisciplineId(), conflict.getTaskId(),
					conflict.getDetails(), conflict.getDetectedAt());
		}
	}

	public record ReleaseRequest(@NotNull UUID sprintId, @NotNull UUID sagaId) {
	}

	public record ReleaseResponse(int releasedTasks) {
	}

	public record ReleaseTaskRequest(@NotNull UUID taskId, @NotNull UUID sagaId) {
	}

	public record ReleaseTaskResponse(int releasedAllocations) {
	}

	private PlanningDtos() {
	}
}
