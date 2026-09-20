package project.timeline.planning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.timeline.common.events.DomainEvent;
import project.timeline.common.events.Topics;
import project.timeline.common.events.planning.PlanningEvents;
import project.timeline.common.outbox.OutboxPublisher;
import project.timeline.common.web.RequestCorrelationFilter;
import project.timeline.common.web.security.CurrentUser;
import project.timeline.planning.domain.Allocation;
import project.timeline.planning.domain.PlanConflict;
import project.timeline.planning.replica.SprintCapacity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PlanningEventPublisher {

	private final OutboxPublisher outbox;
	private final UUID currentPlanId;

	public PlanningEventPublisher(OutboxPublisher outbox,
			@Value("${timeline.planning.current-plan-id}") UUID currentPlanId) {
		this.outbox = outbox;
		this.currentPlanId = currentPlanId;
	}

	public void planRecalculated(List<Allocation> allocations, List<SprintCapacity> capacities,
			List<PlanConflict> conflicts) {
		Map<String, BigDecimal> allocated = new HashMap<>();
		for (Allocation allocation : allocations) {
			allocated.merge(allocation.getSprintId() + "|" + allocation.getDisciplineId(),
					allocation.getPlannedSp(), BigDecimal::add);
		}

		outbox.publish(Topics.PLANNING_ALLOCATION, DomainEvent.of(
				PlanningEvents.PLAN_RECALCULATED,
				"PlanVersion",
				currentPlanId,
				safeActor(),
				RequestCorrelationFilter.currentTraceId(),
				new PlanningEvents.PlanSnapshotPayload(
						currentPlanId,
						allocations.stream()
								.map(a -> new PlanningEvents.AllocationPayload(
										a.getTaskId(), a.getSprintId(), a.getDisciplineId(), a.getPlannedSp()))
								.toList(),
						capacities.stream()
								.map(c -> new PlanningEvents.CapacityPayload(
										c.getSprintId(),
										c.getDisciplineId(),
										c.getAvailablePersonDays(),
										c.getVacationPersonDays(),
										c.getCapacitySp(),
										allocated.getOrDefault(c.getSprintId() + "|" + c.getDisciplineId(),
												BigDecimal.ZERO)))
								.toList(),
						conflicts.stream()
								.map(c -> new PlanningEvents.ConflictPayload(
										c.getId(), c.getType().name(), c.getSeverity().name(),
										c.getSprintId(), c.getDisciplineId(), c.getTaskId(), c.getDetails()))
								.toList())));
	}

	private String safeActor() {
		try {
			return CurrentUser.requireUserId();
		}
		catch (RuntimeException e) {
			return "system";
		}
	}
}
