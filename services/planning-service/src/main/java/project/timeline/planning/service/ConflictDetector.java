package project.timeline.planning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.planning.domain.Allocation;
import project.timeline.planning.domain.ConflictType;
import project.timeline.planning.domain.PlanConflict;
import project.timeline.planning.domain.PlanGraph;
import project.timeline.planning.replica.RefSprint;
import project.timeline.planning.replica.RefTask;
import project.timeline.planning.replica.RefTaskEstimate;
import project.timeline.planning.replica.RefTaskLink;
import project.timeline.planning.replica.SprintCapacity;
import project.timeline.planning.repository.AllocationRepository;
import project.timeline.planning.repository.PlanConflictRepository;
import project.timeline.planning.repository.RefSprintRepository;
import project.timeline.planning.repository.RefTaskEstimateRepository;
import project.timeline.planning.repository.RefTaskLinkRepository;
import project.timeline.planning.repository.RefTaskRepository;
import project.timeline.planning.repository.SprintCapacityRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Материализует конфликты плана.
 *
 * <p>Перегруз ёмкости — предупреждение, а не запрет: из этого следует, что
 * кросс-сервисный инвариант «не превышаем ёмкость» не является инвариантом и не
 * требует синхронных проверок.
 */
@Service
public class ConflictDetector {

	private final AllocationRepository allocations;
	private final PlanConflictRepository conflicts;
	private final SprintCapacityRepository capacities;
	private final RefSprintRepository sprints;
	private final RefTaskRepository tasks;
	private final RefTaskEstimateRepository estimates;
	private final RefTaskLinkRepository links;
	private final UUID currentPlanId;

	public ConflictDetector(AllocationRepository allocations, PlanConflictRepository conflicts,
			SprintCapacityRepository capacities, RefSprintRepository sprints,
			RefTaskRepository tasks, RefTaskEstimateRepository estimates, RefTaskLinkRepository links,
			@Value("${timeline.planning.current-plan-id}") UUID currentPlanId) {
		this.allocations = allocations;
		this.conflicts = conflicts;
		this.capacities = capacities;
		this.sprints = sprints;
		this.tasks = tasks;
		this.estimates = estimates;
		this.links = links;
		this.currentPlanId = currentPlanId;
	}

	@Transactional
	public List<PlanConflict> recalculate() {
		List<Allocation> plan = allocations.findAllByPlanVersionId(currentPlanId);
		List<PlanConflict> detected = new ArrayList<>();
		detected.addAll(capacityOverflows(plan));
		detected.addAll(linkConflicts(plan));
		detected.addAll(blockedTasks(plan));
		detected.addAll(estimateIssues(plan));
		detected.addAll(unplannedTasks(plan));

		conflicts.deleteAllByPlanVersionId(currentPlanId);
		return conflicts.saveAll(detected);
	}

	@Transactional(readOnly = true)
	public List<PlanConflict> current() {
		return conflicts.findAllByPlanVersionIdOrderBySeverityAscTypeAsc(currentPlanId);
	}

	private List<PlanConflict> capacityOverflows(List<Allocation> plan) {
		Map<String, BigDecimal> allocated = new HashMap<>();
		for (Allocation allocation : plan) {
			allocated.merge(allocation.getSprintId() + "|" + allocation.getDisciplineId(),
					allocation.getPlannedSp(), BigDecimal::add);
		}

		List<PlanConflict> result = new ArrayList<>();
		for (SprintCapacity capacity : capacities.findAll()) {
			BigDecimal used = allocated.getOrDefault(
					capacity.getSprintId() + "|" + capacity.getDisciplineId(), BigDecimal.ZERO);
			if (used.compareTo(capacity.getCapacitySp()) > 0) {
				result.add(conflict(ConflictType.CAPACITY_OVERFLOW, capacity.getSprintId(),
						capacity.getDisciplineId(), null,
						"Занято " + used + " SP при ёмкости " + capacity.getCapacitySp() + " SP"));
			}
		}
		return result;
	}

	private List<PlanConflict> linkConflicts(List<Allocation> plan) {
		Map<UUID, Integer> sprintNumber = sprints.findAll().stream()
				.collect(Collectors.toMap(RefSprint::getId, RefSprint::getNumber));

		Map<UUID, Span> spanByTask = new HashMap<>();
		for (Allocation allocation : plan) {
			Integer number = sprintNumber.get(allocation.getSprintId());
			if (number == null) {
				continue;
			}
			spanByTask.merge(allocation.getTaskId(), new Span(number, number), Span::union);
		}

		List<RefTaskLink> allLinks = links.findAll();
		PlanGraph graph = new PlanGraph(
				spanByTask.keySet(),
				allLinks.stream()
						.filter(link -> link.movesPlan() || link.isSimultaneous())
						.map(link -> new PlanGraph.Link(
								link.getFromTaskId(),
								link.getToTaskId(),
								link.isSimultaneous() ? PlanGraph.Kind.SIMULTANEOUS : PlanGraph.Kind.ORDERED))
						.toList());

		List<PlanConflict> result = new ArrayList<>();

		for (RefTaskLink link : allLinks) {
			Span from = spanByTask.get(link.getFromTaskId());
			Span to = spanByTask.get(link.getToTaskId());
			if (from == null || to == null) {
				continue;
			}

			if (link.isSimultaneous()) {
				if (from.first != to.first || from.last != to.last) {
					result.add(conflict(ConflictType.SIMULTANEITY_VIOLATION, null, null, link.getToTaskId(),
							"Задачи «ставим вместе» стоят в разных спринтах"));
				}
				continue;
			}

			// first(B) > last(A)
			if (!(to.first > from.last)) {
				ConflictType.Severity severity = link.isHard()
						? ConflictType.Severity.ERROR
						: ConflictType.Severity.WARNING;
				result.add(new PlanConflict(currentPlanId, ConflictType.SEQUENCE_VIOLATION, severity,
						null, null, link.getToTaskId(),
						"Нарушен порядок: последующая задача начинается не позже предшественника"));
			}
		}

		for (UUID group : graph.groups()) {
			Set<UUID> members = graph.tasksOf(group);
			if (members.size() < 2) {
				continue;
			}
			Span expected = null;
			for (UUID taskId : members) {
				Span span = spanByTask.get(taskId);
				if (span == null) {
					continue;
				}
				if (expected == null) {
					expected = span;
				}
				else if (expected.first != span.first || expected.last != span.last) {
					result.add(conflict(ConflictType.SIMULTANEITY_VIOLATION, null, null, taskId,
							"Группа «ставим вместе» разъехалась по спринтам"));
					break;
				}
			}
		}
		return result;
	}

	private List<PlanConflict> blockedTasks(List<Allocation> plan) {
		Set<UUID> planned = plan.stream().map(Allocation::getTaskId).collect(Collectors.toSet());
		Map<UUID, RefTask> taskById = tasks.findAll().stream()
				.collect(Collectors.toMap(RefTask::getId, task -> task));

		List<PlanConflict> result = new ArrayList<>();
		for (RefTaskLink link : links.findAll()) {
			if (!link.isBlocking() || !planned.contains(link.getToTaskId())) {
				continue;
			}
			RefTask blocker = taskById.get(link.getFromTaskId());
			if (blocker != null && !blocker.isDone()) {
				result.add(conflict(ConflictType.BLOCKED_TASK, null, null, link.getToTaskId(),
						"Блокирующая задача " + blocker.getKey() + " ещё не завершена"));
			}
		}
		return result;
	}

	private List<PlanConflict> estimateIssues(List<Allocation> plan) {
		List<PlanConflict> result = new ArrayList<>();
		Map<UUID, List<RefTaskEstimate>> byTask = estimates.findAll().stream()
				.collect(Collectors.groupingBy(RefTaskEstimate::getTaskId));

		Map<String, BigDecimal> allocated = new HashMap<>();
		for (Allocation allocation : plan) {
			allocated.merge(allocation.getTaskId() + "|" + allocation.getDisciplineId(),
					allocation.getPlannedSp(), BigDecimal::add);
		}

		for (UUID taskId : plan.stream().map(Allocation::getTaskId).collect(Collectors.toSet())) {
			for (RefTaskEstimate estimate : byTask.getOrDefault(taskId, List.of())) {
				if (estimate.getEstimateSp() == null) {
					result.add(conflict(ConflictType.MISSING_ESTIMATE, null,
							estimate.getDisciplineId(), taskId,
							"Роль задействована, но оценка в SP не проставлена"));
					continue;
				}
				BigDecimal used = allocated.getOrDefault(
						taskId + "|" + estimate.getDisciplineId(), BigDecimal.ZERO);
				if (used.compareTo(estimate.getEstimateSp()) != 0) {
					result.add(conflict(ConflictType.OVER_ALLOCATED_TASK, null,
							estimate.getDisciplineId(), taskId,
							"Аллоцировано " + used + " SP при оценке " + estimate.getEstimateSp() + " SP"));
				}
			}
		}
		return result;
	}

	private List<PlanConflict> unplannedTasks(List<Allocation> plan) {
		Set<UUID> planned = plan.stream().map(Allocation::getTaskId).collect(Collectors.toCollection(HashSet::new));
		List<PlanConflict> result = new ArrayList<>();
		for (RefTask task : tasks.findAll()) {
			if (task.needsPlanning() && !planned.contains(task.getId())) {
				result.add(conflict(ConflictType.UNPLANNED_TASK, null, null, task.getId(),
						"Задача " + task.getKey() + " не размещена ни в одном спринте"));
			}
		}
		return result;
	}

	private PlanConflict conflict(ConflictType type, UUID sprintId, UUID disciplineId, UUID taskId, String details) {
		return new PlanConflict(currentPlanId, type, type.defaultSeverity(),
				sprintId, disciplineId, taskId, details);
	}

	private record Span(int first, int last) {

		Span union(Span other) {
			return new Span(Math.min(first, other.first), Math.max(last, other.last));
		}
	}
}
