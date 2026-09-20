package project.timeline.bff.board;

import org.springframework.stereotype.Component;
import project.timeline.bff.projection.DisciplineProjRepository;
import project.timeline.bff.projection.EpicProjRepository;
import project.timeline.bff.projection.LinkProjRepository;
import project.timeline.bff.projection.PlanProjRepository;
import project.timeline.bff.projection.Projections;
import project.timeline.bff.projection.SprintProjRepository;
import project.timeline.bff.projection.TaskProjRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class BoardAssembler {

	private static final List<String> ROLE_ORDER = List.of("BE", "FE", "QA", "SA");

	private final SprintProjRepository sprints;
	private final EpicProjRepository epics;
	private final TaskProjRepository tasks;
	private final LinkProjRepository links;
	private final DisciplineProjRepository disciplines;
	private final PlanProjRepository plans;

	public BoardAssembler(SprintProjRepository sprints, EpicProjRepository epics, TaskProjRepository tasks,
			LinkProjRepository links, DisciplineProjRepository disciplines, PlanProjRepository plans) {
		this.sprints = sprints;
		this.epics = epics;
		this.tasks = tasks;
		this.links = links;
		this.disciplines = disciplines;
		this.plans = plans;
	}

	public BoardDocument assemble(long revision) {
		Projections.PlanProj plan = plans.findById("plan:current").orElseGet(Projections.PlanProj::new);
		if (plan.allocations == null) {
			plan.allocations = new ArrayList<>();
		}
		if (plan.capacities == null) {
			plan.capacities = new ArrayList<>();
		}
		if (plan.conflicts == null) {
			plan.conflicts = new ArrayList<>();
		}
		List<Projections.DisciplineProj> allDisciplines = disciplines.findAll().stream()
				.filter(discipline -> discipline != null && discipline.id != null && discipline.code != null)
				.sorted(Comparator.comparingInt(BoardAssembler::roleRank)
						.thenComparing(d -> d.code, String.CASE_INSENSITIVE_ORDER))
				.toList();
		Map<UUID, String> disciplineCode = allDisciplines.stream()
				.collect(Collectors.toMap(d -> d.id, d -> d.code, (a, b) -> a));
		Map<UUID, String> taskKey = tasks.findAll().stream()
				.filter(task -> task != null && task.id != null)
				.collect(Collectors.toMap(t -> t.id, t -> t.key == null ? t.id.toString() : t.key, (a, b) -> a));

		Map<String, List<String>> conflictsByCell = new HashMap<>();
		Map<String, Integer> summary = new LinkedHashMap<>();
		for (Projections.Conflict conflict : plan.conflicts) {
			if (conflict == null || conflict.severity == null) {
				continue;
			}
			summary.merge(conflict.severity, 1, Integer::sum);
			if (conflict.taskId != null && conflict.sprintId != null && conflict.disciplineId != null) {
				conflictsByCell
						.computeIfAbsent(conflict.taskId + "|" + conflict.sprintId + "|" + conflict.disciplineId,
								key -> new ArrayList<>())
						.add(conflict.type);
			}
		}

		Map<String, Projections.Capacity> capacityIndex = plan.capacities.stream()
				.filter(capacity -> capacity != null && capacity.sprintId != null && capacity.disciplineId != null)
				.collect(Collectors.toMap(
						capacity -> capacity.sprintId + "|" + capacity.disciplineId,
						capacity -> capacity,
						(a, b) -> a));

		List<BoardDocument.SprintColumn> sprintColumns = sprints.findAllByOrderByNumberAsc().stream()
				.filter(sprint -> sprint != null && sprint.id != null)
				.map(sprint -> new BoardDocument.SprintColumn(
						sprint.id,
						sprint.number,
						sprint.name,
						sprint.startDate,
						sprint.endDate,
						workingDaysBetween(sprint.startDate, sprint.endDate),
						capacityForSprint(sprint.id, allDisciplines, capacityIndex, disciplineCode)))
				.toList();

		Map<UUID, List<Projections.TaskProj>> tasksByEpic = tasks.findAllByOrderByPriorityAscKeyAsc().stream()
				.filter(task -> task != null && task.id != null)
				.collect(Collectors.groupingBy(task -> task.epicId == null
						? NIL_EPIC
						: task.epicId, LinkedHashMap::new, Collectors.toList()));

		List<Projections.LinkProj> allLinks = links.findAll().stream()
				.filter(link -> link != null && link.fromTaskId != null && link.toTaskId != null)
				.toList();
		List<BoardDocument.EpicRow> epicRows = new ArrayList<>();
		for (Projections.EpicProj epic : epics.findAllByOrderByOrderIndexAsc()) {
			if (epic == null || epic.id == null) {
				continue;
			}
			epicRows.add(toEpicRow(epic, tasksByEpic.getOrDefault(epic.id, List.of()),
					plan, allLinks, taskKey, conflictsByCell));
			tasksByEpic.remove(epic.id);
		}
		List<Projections.TaskProj> orphanTasks = tasksByEpic.values().stream().flatMap(List::stream).toList();
		if (!orphanTasks.isEmpty()) {
			epicRows.add(toEpicRow(null, orphanTasks, plan, allLinks, taskKey, conflictsByCell));
		}

		BoardDocument board = new BoardDocument();
		board.setRevision(revision);
		board.setUpdatedAt(Instant.now());
		board.setSprints(sprintColumns);
		board.setEpics(epicRows);
		board.setConflictsSummary(summary);
		return board;
	}

	/**
	 * В шапке спринта всегда все известные роли (FE/BE/QA/SA…): даже при нулевой
	 * ёмкости строка нужна, иначе на доске «пропадает» незадействованная роль.
	 */
	private List<BoardDocument.CapacityCell> capacityForSprint(
			UUID sprintId,
			List<Projections.DisciplineProj> allDisciplines,
			Map<String, Projections.Capacity> capacityIndex,
			Map<UUID, String> disciplineCode) {
		List<BoardDocument.CapacityCell> cells = new ArrayList<>();
		for (Projections.DisciplineProj discipline : allDisciplines) {
			Projections.Capacity capacity = capacityIndex.get(sprintId + "|" + discipline.id);
			if (capacity != null) {
				cells.add(toCapacityCell(capacity, disciplineCode));
			}
			else {
				cells.add(new BoardDocument.CapacityCell(
						discipline.id,
						discipline.code,
						0,
						0,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						false));
			}
		}
		return cells;
	}

	private BoardDocument.CapacityCell toCapacityCell(Projections.Capacity capacity,
			Map<UUID, String> disciplineCode) {
		BigDecimal capacitySp = capacity.capacitySp == null ? BigDecimal.ZERO : capacity.capacitySp;
		BigDecimal allocated = capacity.allocatedSp == null ? BigDecimal.ZERO : capacity.allocatedSp;
		BigDecimal free = capacitySp.subtract(allocated);
		return new BoardDocument.CapacityCell(
				capacity.disciplineId,
				disciplineCode.getOrDefault(capacity.disciplineId, "?"),
				capacity.vacationPersonDays,
				capacity.availablePersonDays,
				capacitySp,
				allocated,
				free,
				free.signum() < 0);
	}

	private BoardDocument.EpicRow toEpicRow(Projections.EpicProj epic, List<Projections.TaskProj> epicTasks,
			Projections.PlanProj plan, List<Projections.LinkProj> allLinks, Map<UUID, String> taskKey,
			Map<String, List<String>> conflictsByCell) {
		List<BoardDocument.TaskRow> rows = epicTasks.stream()
				.sorted(Comparator.comparingInt((Projections.TaskProj task) -> task.priority)
						.thenComparing(task -> task.key))
				.map(task -> toTaskRow(task, plan, allLinks, taskKey, conflictsByCell))
				.toList();
		if (epic == null) {
			return new BoardDocument.EpicRow(null, "NONE", "Без эпика", null, rows);
		}
		return new BoardDocument.EpicRow(epic.id, epic.key, epic.name, epic.color, rows);
	}

	private BoardDocument.TaskRow toTaskRow(Projections.TaskProj task, Projections.PlanProj plan,
			List<Projections.LinkProj> allLinks, Map<UUID, String> taskKey,
			Map<String, List<String>> conflictsByCell) {
		List<Projections.Estimate> estimateSource = task.estimates == null ? List.of() : task.estimates;
		List<BoardDocument.EstimateCell> estimates = estimateSource.stream()
				.map(estimate -> new BoardDocument.EstimateCell(estimate.disciplineId, estimate.estimateSp))
				.toList();
		List<Projections.Allocation> allocationSource = plan.allocations == null ? List.of() : plan.allocations;
		List<BoardDocument.AllocationCell> cells = allocationSource.stream()
				.filter(allocation -> allocation.taskId != null && allocation.taskId.equals(task.id))
				.map(allocation -> new BoardDocument.AllocationCell(
						allocation.sprintId,
						allocation.disciplineId,
						allocation.plannedSp == null ? BigDecimal.ZERO : allocation.plannedSp,
						conflictsByCell.getOrDefault(
								task.id + "|" + allocation.sprintId + "|" + allocation.disciplineId,
								List.of())))
				.toList();
		List<BoardDocument.LinkCell> linkCells = allLinks.stream()
				.filter(link -> link.fromTaskId.equals(task.id) || link.toTaskId.equals(task.id))
				.map(link -> toLinkCell(task.id, link, taskKey))
				.toList();
		return new BoardDocument.TaskRow(task.id, task.key, task.title, task.status, estimates, cells, linkCells);
	}

	private BoardDocument.LinkCell toLinkCell(UUID taskId, Projections.LinkProj link, Map<UUID, String> taskKey) {
		if ("SIMULTANEOUS".equals(link.type)) {
			UUID other = link.fromTaskId.equals(taskId) ? link.toTaskId : link.fromTaskId;
			return new BoardDocument.LinkCell(link.type, link.hardness, "WITH",
					taskKey.getOrDefault(other, other.toString()));
		}
		if (link.fromTaskId.equals(taskId)) {
			String direction = "BLOCKING".equals(link.type) ? "BLOCKS" : "BEFORE";
			return new BoardDocument.LinkCell(link.type, link.hardness, direction,
					taskKey.getOrDefault(link.toTaskId, link.toTaskId.toString()));
		}
		return new BoardDocument.LinkCell(link.type, link.hardness, "AFTER",
				taskKey.getOrDefault(link.fromTaskId, link.fromTaskId.toString()));
	}

	static int workingDaysBetween(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			return 0;
		}
		int days = 0;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			DayOfWeek dow = day.getDayOfWeek();
			if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
				days++;
			}
		}
		return days;
	}

	private static int roleRank(Projections.DisciplineProj discipline) {
		int index = ROLE_ORDER.indexOf(discipline.code);
		return index < 0 ? ROLE_ORDER.size() : index;
	}

	private static final UUID NIL_EPIC = UUID.fromString("00000000-0000-0000-0000-000000000000");
}
