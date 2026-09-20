package project.timeline.planning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.web.DomainException;
import project.timeline.planning.domain.Allocation;
import project.timeline.planning.domain.CascadePlanner;
import project.timeline.planning.domain.PlanConflict;
import project.timeline.planning.domain.PlanGraph;
import project.timeline.planning.replica.RefSprint;
import project.timeline.planning.replica.RefTaskLink;
import project.timeline.planning.replica.SprintCapacity;
import project.timeline.planning.repository.AllocationRepository;
import project.timeline.planning.repository.RefSprintRepository;
import project.timeline.planning.repository.RefTaskLinkRepository;
import project.timeline.planning.repository.RefTaskRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanningService {

	private final AllocationRepository allocations;
	private final RefSprintRepository sprints;
	private final RefTaskRepository tasks;
	private final RefTaskLinkRepository links;
	private final CapacityService capacityService;
	private final ConflictDetector conflictDetector;
	private final PlanningEventPublisher events;
	private final UUID currentPlanId;

	public PlanningService(AllocationRepository allocations, RefSprintRepository sprints,
			RefTaskRepository tasks, RefTaskLinkRepository links, CapacityService capacityService,
			ConflictDetector conflictDetector, PlanningEventPublisher events,
			@Value("${timeline.planning.current-plan-id}") UUID currentPlanId) {
		this.allocations = allocations;
		this.sprints = sprints;
		this.tasks = tasks;
		this.links = links;
		this.capacityService = capacityService;
		this.conflictDetector = conflictDetector;
		this.events = events;
		this.currentPlanId = currentPlanId;
	}

	@Transactional(readOnly = true)
	public List<Allocation> findAll() {
		return allocations.findAllByPlanVersionId(currentPlanId);
	}

	@Transactional(readOnly = true)
	public List<SprintCapacity> capacities() {
		return capacityService.current();
	}

	@Transactional(readOnly = true)
	public List<PlanConflict> conflicts() {
		return conflictDetector.current();
	}

	/**
	 * Полностью заменяет аллокации одной задачи. Пустой список снимает задачу с
	 * плана. Перегруз ёмкости не блокирует сохранение — он появится как конфликт.
	 */
	@Transactional
	public List<Allocation> putTaskAllocations(UUID taskId, List<AllocationDraft> drafts) {
		if (!tasks.existsById(taskId)) {
			throw DomainException.notFound("TASK_NOT_FOUND", "Задача не найдена в реплике: " + taskId);
		}

		Map<UUID, RefSprint> sprintById = sprints.findAll().stream()
				.collect(Collectors.toMap(RefSprint::getId, sprint -> sprint));

		List<Allocation> replacement = new ArrayList<>();
		for (AllocationDraft draft : drafts) {
			if (!sprintById.containsKey(draft.sprintId())) {
				throw DomainException.notFound("SPRINT_NOT_FOUND", "Спринт не найден: " + draft.sprintId());
			}
			try {
				replacement.add(new Allocation(currentPlanId, taskId, draft.sprintId(),
						draft.disciplineId(), draft.plannedSp()));
			}
			catch (IllegalArgumentException e) {
				throw DomainException.badRequest("ALLOCATION_INVALID", e.getMessage());
			}
		}

		allocations.findAllByPlanVersionIdAndTaskId(currentPlanId, taskId)
				.forEach(allocations::delete);
		List<Allocation> saved = allocations.saveAll(replacement);
		conflictDetector.recalculate();
		publishSnapshot();
		return saved;
	}

	/**
	 * Перенос задачи с каскадом по связям.
	 *
	 * <p>{@code preview=true} считает последствия, но не пишет их: администратор
	 * должен увидеть, какие задачи поедут следом, до применения.
	 */
	@Transactional
	public MoveResult moveTask(UUID taskId, UUID toSprintId, boolean preview) {
		List<RefSprint> orderedSprints = sprints.findAllByOrderByNumberAsc();
		Map<UUID, Integer> indexById = new HashMap<>();
		for (int i = 0; i < orderedSprints.size(); i++) {
			indexById.put(orderedSprints.get(i).getId(), i);
		}
		Integer targetIndex = indexById.get(toSprintId);
		if (targetIndex == null) {
			throw DomainException.notFound("SPRINT_NOT_FOUND", "Спринт не найден: " + toSprintId);
		}
		if (!tasks.existsById(taskId)) {
			throw DomainException.notFound("TASK_NOT_FOUND", "Задача не найдена: " + taskId);
		}

		List<Allocation> plan = allocations.findAllByPlanVersionId(currentPlanId);
		Map<UUID, CascadePlanner.Span> spans = buildSpans(plan, indexById);
		if (!spans.containsKey(taskId)) {
			throw DomainException.badRequest("TASK_UNPLANNED",
					"Задача не размещена ни в одном спринте, переносить нечего");
		}

		PlanGraph graph = buildGraph(spans.keySet());
		Map<UUID, Integer> shifts;
		try {
			shifts = new CascadePlanner(graph, spans).plan(taskId, targetIndex);
		}
		catch (IllegalStateException e) {
			throw DomainException.conflict("PLAN_CYCLE", e.getMessage());
		}

		if (preview) {
			return new MoveResult(true, shifts, List.of());
		}

		applyShifts(plan, orderedSprints, indexById, shifts);
		List<PlanConflict> conflicts = conflictDetector.recalculate();
		publishSnapshot();
		return new MoveResult(false, shifts, conflicts);
	}

	/**
	 * Шаг саги удаления спринта: снимает все аллокации спринта. Идемпотентен по
	 * факту — повторный вызов на уже пустом спринте просто вернёт ноль.
	 */
	@Transactional
	public int releaseSprintAllocations(UUID sprintId) {
		List<Allocation> toRemove = allocations.findAllByPlanVersionIdAndSprintId(currentPlanId, sprintId);
		Set<UUID> affectedTasks = toRemove.stream().map(Allocation::getTaskId).collect(Collectors.toSet());
		allocations.deleteAll(toRemove);
		conflictDetector.recalculate();
		publishSnapshot();
		return affectedTasks.size();
	}

	/**
	 * Шаг саги удаления задачи: снимает все её аллокации. Идемпотентен — повтор
	 * на уже пустой задаче возвращает ноль. Параметр {@code sagaId} нужен
	 * вызывающей стороне для идемпотентности оркестрации; эффект здесь
	 * определяется только {@code taskId}.
	 */
	@Transactional
	public int releaseTaskAllocations(UUID taskId) {
		List<Allocation> toRemove = allocations.findAllByPlanVersionIdAndTaskId(currentPlanId, taskId);
		allocations.deleteAll(toRemove);
		conflictDetector.recalculate();
		publishSnapshot();
		return toRemove.size();
	}

	@Transactional
	public void recalculateAll() {
		capacityService.recalculateAll();
		conflictDetector.recalculate();
		publishSnapshot();
	}

	private void applyShifts(List<Allocation> plan, List<RefSprint> orderedSprints,
			Map<UUID, Integer> indexById, Map<UUID, Integer> shifts) {
		for (Allocation allocation : plan) {
			Integer shift = shifts.get(allocation.getTaskId());
			if (shift == null || shift == 0) {
				continue;
			}
			Integer currentIndex = indexById.get(allocation.getSprintId());
			if (currentIndex == null) {
				continue;
			}
			int newIndex = currentIndex + shift;
			if (newIndex < 0 || newIndex >= orderedSprints.size()) {
				throw DomainException.conflict("MOVE_OUT_OF_RANGE",
						"Каскад вывел задачу за пределы известных спринтов");
			}
			allocation.moveTo(orderedSprints.get(newIndex).getId());
		}
	}

	private Map<UUID, CascadePlanner.Span> buildSpans(List<Allocation> plan, Map<UUID, Integer> indexById) {
		Map<UUID, CascadePlanner.Span> spans = new HashMap<>();
		for (Allocation allocation : plan) {
			Integer index = indexById.get(allocation.getSprintId());
			if (index == null) {
				continue;
			}
			spans.merge(allocation.getTaskId(), new CascadePlanner.Span(index, index), CascadePlanner.Span::union);
		}
		return spans;
	}

	private PlanGraph buildGraph(Set<UUID> taskIds) {
		List<PlanGraph.Link> graphLinks = links.findAll().stream()
				.filter(link -> link.movesPlan() || link.isSimultaneous())
				.map(this::toGraphLink)
				.toList();
		return new PlanGraph(taskIds, graphLinks);
	}

	private PlanGraph.Link toGraphLink(RefTaskLink link) {
		return new PlanGraph.Link(
				link.getFromTaskId(),
				link.getToTaskId(),
				link.isSimultaneous() ? PlanGraph.Kind.SIMULTANEOUS : PlanGraph.Kind.ORDERED);
	}

	private void publishSnapshot() {
		events.planRecalculated(
				allocations.findAllByPlanVersionId(currentPlanId),
				capacityService.current(),
				conflictDetector.current());
	}

	public record AllocationDraft(UUID sprintId, UUID disciplineId, BigDecimal plannedSp) {
	}

	public record MoveResult(boolean preview, Map<UUID, Integer> shifts, List<PlanConflict> conflicts) {

		public Map<UUID, Integer> shiftedTasks() {
			return shifts.entrySet().stream()
					.filter(entry -> entry.getValue() != 0)
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
							(a, b) -> a, LinkedHashMap::new));
		}
	}
}
