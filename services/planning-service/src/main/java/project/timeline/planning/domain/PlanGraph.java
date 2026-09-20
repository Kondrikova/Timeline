package project.timeline.planning.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Граф связей для планирования: группы совместности свёрнуты в супер-узлы.
 *
 * <p>Свёртка выполняется до топологической сортировки, иначе два вида ограничений
 * невозможно совместить: задачи, помеченные «ставим вместе», должны двигаться
 * только целиком, а порядок считается уже между такими блоками.
 *
 * <p>Класс намеренно не переиспользуется из backlog-service: там граф отвечает за
 * допустимость связи, здесь — за порядок планирования. Общий доменный код между
 * сервисами превратил бы систему в распределённый монолит.
 */
public final class PlanGraph {

	private final Map<UUID, UUID> parent = new HashMap<>();
	private final Map<UUID, Set<UUID>> members = new HashMap<>();
	private final Map<UUID, Set<UUID>> edges = new HashMap<>();

	public PlanGraph(Collection<UUID> taskIds, Collection<Link> links) {
		taskIds.forEach(id -> parent.put(id, id));
		links.stream()
				.filter(link -> link.kind() == Kind.SIMULTANEOUS)
				.forEach(link -> union(link.from(), link.to()));

		for (UUID taskId : parent.keySet()) {
			members.computeIfAbsent(root(taskId), key -> new LinkedHashSet<>()).add(taskId);
		}

		links.stream()
				.filter(link -> link.kind() == Kind.ORDERED)
				.forEach(link -> {
					UUID from = root(link.from());
					UUID to = root(link.to());
					if (!from.equals(to)) {
						edges.computeIfAbsent(from, key -> new HashSet<>()).add(to);
					}
				});
	}

	public UUID groupOf(UUID taskId) {
		return root(taskId);
	}

	public Set<UUID> groups() {
		return Set.copyOf(members.keySet());
	}

	public Set<UUID> tasksOf(UUID group) {
		return members.getOrDefault(group, Set.of());
	}

	public Set<UUID> successors(UUID group) {
		return edges.getOrDefault(group, Set.of());
	}

	/** Супер-узлы в порядке «предшественники раньше последователей». */
	public List<UUID> topologicalOrder() {
		Map<UUID, Integer> inDegree = new HashMap<>();
		members.keySet().forEach(group -> inDegree.put(group, 0));
		edges.forEach((from, targets) -> targets.forEach(to -> inDegree.merge(to, 1, Integer::sum)));

		Deque<UUID> ready = new ArrayDeque<>();
		inDegree.forEach((group, degree) -> {
			if (degree == 0) {
				ready.add(group);
			}
		});

		List<UUID> order = new ArrayList<>();
		while (!ready.isEmpty()) {
			UUID current = ready.poll();
			order.add(current);
			for (UUID next : successors(current)) {
				if (inDegree.merge(next, -1, Integer::sum) == 0) {
					ready.add(next);
				}
			}
		}
		if (order.size() != inDegree.size()) {
			throw new IllegalStateException("В графе связей обнаружен цикл, план построить нельзя");
		}
		return order;
	}

	private UUID root(UUID taskId) {
		UUID current = parent.getOrDefault(taskId, taskId);
		while (!current.equals(parent.getOrDefault(current, current))) {
			current = parent.get(current);
		}
		return current;
	}

	private void union(UUID left, UUID right) {
		parent.putIfAbsent(left, left);
		parent.putIfAbsent(right, right);
		UUID leftRoot = root(left);
		UUID rightRoot = root(right);
		if (!leftRoot.equals(rightRoot)) {
			parent.put(rightRoot, leftRoot);
		}
	}

	/**
	 * Мягкие связи в каскад не входят: они выражают предпочтение, а не
	 * необходимость, и план не двигают — их нарушение лишь подсвечивается.
	 */
	public record Link(UUID from, UUID to, Kind kind) {
	}

	public enum Kind {
		ORDERED,
		SIMULTANEOUS
	}
}
