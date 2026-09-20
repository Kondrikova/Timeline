package project.timeline.planning.domain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Каскадный сдвиг задач по связям.
 *
 * <p>Планирование ведётся в спринтах, поэтому сдвиг измеряется в их количестве, а
 * не в днях, и вместо планировщика критического пути достаточно топологической
 * сортировки с одним прямым проходом.
 */
public final class CascadePlanner {

	private final PlanGraph graph;
	private final Map<UUID, Span> taskSpans;

	/**
	 * @param taskSpans занятые задачей спринты в виде порядковых номеров в общем
	 *                  списке спринтов; задачи без аллокаций в расчёт не входят
	 */
	public CascadePlanner(PlanGraph graph, Map<UUID, Span> taskSpans) {
		this.graph = graph;
		this.taskSpans = Map.copyOf(taskSpans);
	}

	/**
	 * @param movedTaskId    задача, которую двигает администратор
	 * @param targetFirstIndex новый номер первого спринта задачи
	 * @return сдвиг в спринтах для каждой затронутой задачи, включая исходную
	 */
	public Map<UUID, Integer> plan(UUID movedTaskId, int targetFirstIndex) {
		Map<UUID, Span> groupSpans = collapseToGroups();

		UUID movedGroup = graph.groupOf(movedTaskId);
		Span movedSpan = groupSpans.get(movedGroup);
		if (movedSpan == null) {
			throw new IllegalArgumentException("Задача не размещена ни в одном спринте");
		}

		Map<UUID, Integer> groupShift = new HashMap<>();
		groupShift.put(movedGroup, targetFirstIndex - movedSpan.first());

		// Прямой проход: каждая группа сдвигается ровно настолько, насколько её
		// толкают предшественники, и ни на спринт больше. Топологический порядок
		// гарантирует, что к моменту обработки группы её собственный сдвиг уже
		// окончателен.
		for (UUID group : graph.topologicalOrder()) {
			Span span = groupSpans.get(group);
			if (span == null) {
				continue;
			}
			int last = span.last() + groupShift.getOrDefault(group, 0);

			for (UUID successor : graph.successors(group)) {
				Span successorSpan = groupSpans.get(successor);
				if (successorSpan == null) {
					continue;
				}
				int successorStart = successorSpan.first() + groupShift.getOrDefault(successor, 0);
				int required = last + 1 - successorStart;
				if (required > 0) {
					groupShift.merge(successor, required, Integer::sum);
				}
			}
		}

		Map<UUID, Integer> result = new LinkedHashMap<>();
		groupShift.forEach((group, shift) -> {
			if (shift == 0) {
				return;
			}
			graph.tasksOf(group).stream()
					.filter(taskSpans::containsKey)
					.forEach(taskId -> result.put(taskId, shift));
		});
		return result;
	}

	private Map<UUID, Span> collapseToGroups() {
		Map<UUID, Span> groupSpans = new HashMap<>();
		taskSpans.forEach((taskId, span) -> groupSpans.merge(graph.groupOf(taskId), span, Span::union));
		return groupSpans;
	}

	public Optional<Span> spanOf(UUID taskId) {
		return Optional.ofNullable(taskSpans.get(taskId));
	}

	/** Первый и последний спринт задачи или группы, в порядковых номерах. */
	public record Span(int first, int last) {

		public Span union(Span other) {
			return new Span(Math.min(first, other.first), Math.max(last, other.last));
		}
	}
}
