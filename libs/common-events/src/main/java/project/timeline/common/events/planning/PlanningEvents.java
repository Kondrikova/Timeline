package project.timeline.common.events.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Полезные нагрузки событий, публикуемых planning-service. */
public final class PlanningEvents {

	public static final String PLAN_RECALCULATED = "PlanRecalculated";

	public record AllocationPayload(
			UUID taskId,
			UUID sprintId,
			UUID disciplineId,
			BigDecimal plannedSp) {
	}

	public record CapacityPayload(
			UUID sprintId,
			UUID disciplineId,
			int availablePersonDays,
			int vacationPersonDays,
			BigDecimal capacitySp,
			BigDecimal allocatedSp) {
	}

	public record ConflictPayload(
			UUID id,
			String type,
			String severity,
			UUID sprintId,
			UUID disciplineId,
			UUID taskId,
			String details) {
	}

	/**
	 * Полный снимок плана после пересчёта. BFF подменяет проекцию целиком —
	 * дешевле и надёжнее, чем склеивать инкрементальные дельты аллокаций и конфликтов.
	 */
	public record PlanSnapshotPayload(
			UUID planVersionId,
			List<AllocationPayload> allocations,
			List<CapacityPayload> capacities,
			List<ConflictPayload> conflicts) {
	}

	private PlanningEvents() {
	}
}
