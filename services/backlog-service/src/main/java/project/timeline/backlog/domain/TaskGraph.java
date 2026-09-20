package project.timeline.backlog.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Граф связей между задачами со свёрнутыми группами совместности.
 *
 * <p>Проверять достижимость по исходным рёбрам недостаточно. Пример: A и B
 * помечены «ставим вместе», при этом A → C и C → B. Каждая связь по отдельности
 * корректна, но вместе они требуют, чтобы B был и в одном спринте с A, и позже C,
 * который позже A. Корректного плана не существует. Такие противоречия видны
 * только после свёртки групп совместности в супер-узлы, поэтому проверка живёт
 * здесь, а не в SQL-запросе достижимости.
 *
 * <p>Граф целиком помещается в памяти: речь о сотнях задач одной команды.
 */
public final class TaskGraph {

	private final Map<UUID, UUID> groupOf = new HashMap<>();
	private final Map<UUID, Set<UUID>> edges = new HashMap<>();

	public TaskGraph(Collection<TaskLink> links) {
		links.stream()
				.filter(link -> link.getType() == LinkType.SIMULTANEOUS)
				.forEach(link -> union(link.getFromTaskId(), link.getToTaskId()));
		links.stream()
				.filter(link -> link.getType().isDirected())
				.forEach(link -> addEdge(link.getFromTaskId(), link.getToTaskId()));
	}

	/** Идентификатор супер-узла, в который входит задача. */
	public UUID group(UUID taskId) {
		UUID root = groupOf.get(taskId);
		if (root == null) {
			return taskId;
		}
		while (!root.equals(groupOf.getOrDefault(root, root))) {
			root = groupOf.get(root);
		}
		return root;
	}

	public Set<UUID> groupMembers(UUID taskId) {
		UUID root = group(taskId);
		Set<UUID> members = new HashSet<>();
		members.add(taskId);
		groupOf.keySet().stream().filter(id -> group(id).equals(root)).forEach(members::add);
		return members;
	}

	/** Существует ли путь по направленным связям между супер-узлами. */
	public boolean reachable(UUID fromTaskId, UUID toTaskId) {
		UUID from = group(fromTaskId);
		UUID target = group(toTaskId);
		if (from.equals(target)) {
			return true;
		}
		Deque<UUID> queue = new ArrayDeque<>();
		Set<UUID> visited = new HashSet<>();
		queue.add(from);
		visited.add(from);
		while (!queue.isEmpty()) {
			UUID current = queue.poll();
			for (UUID next : edges.getOrDefault(current, Set.of())) {
				if (next.equals(target)) {
					return true;
				}
				if (visited.add(next)) {
					queue.add(next);
				}
			}
		}
		return false;
	}

	/** Порядок обхода супер-узлов: предшественники раньше последователей. */
	public List<UUID> topologicalOrder() {
		Map<UUID, Integer> inDegree = new HashMap<>();
		edges.forEach((from, targets) -> {
			inDegree.putIfAbsent(from, 0);
			targets.forEach(to -> inDegree.merge(to, 1, Integer::sum));
		});

		Deque<UUID> ready = new ArrayDeque<>();
		inDegree.forEach((node, degree) -> {
			if (degree == 0) {
				ready.add(node);
			}
		});

		List<UUID> order = new ArrayList<>();
		while (!ready.isEmpty()) {
			UUID current = ready.poll();
			order.add(current);
			for (UUID next : edges.getOrDefault(current, Set.of())) {
				if (inDegree.merge(next, -1, Integer::sum) == 0) {
					ready.add(next);
				}
			}
		}
		if (order.size() != inDegree.size()) {
			throw new IllegalStateException("В графе связей обнаружен цикл");
		}
		return order;
	}

	public Set<UUID> successors(UUID taskId) {
		return edges.getOrDefault(group(taskId), Set.of());
	}

	private void addEdge(UUID from, UUID to) {
		edges.computeIfAbsent(group(from), key -> new HashSet<>()).add(group(to));
	}

	private void union(UUID left, UUID right) {
		groupOf.putIfAbsent(left, left);
		groupOf.putIfAbsent(right, right);
		UUID leftRoot = group(left);
		UUID rightRoot = group(right);
		if (!leftRoot.equals(rightRoot)) {
			groupOf.put(rightRoot, leftRoot);
		}
	}
}
