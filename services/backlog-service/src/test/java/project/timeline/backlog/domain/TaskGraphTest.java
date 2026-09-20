package project.timeline.backlog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskGraphTest {

	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();
	private final UUID d = UUID.randomUUID();

	private TaskLink sequential(UUID from, UUID to) {
		return new TaskLink(from, to, LinkType.SEQUENTIAL, LinkHardness.HARD);
	}

	private TaskLink simultaneous(UUID from, UUID to) {
		return new TaskLink(from, to, LinkType.SIMULTANEOUS, LinkHardness.HARD);
	}

	@Test
	@DisplayName("достижимость идёт по цепочке направленных связей")
	void findsTransitiveReachability() {
		TaskGraph graph = new TaskGraph(List.of(sequential(a, b), sequential(b, c)));

		assertThat(graph.reachable(a, c)).isTrue();
		assertThat(graph.reachable(c, a)).isFalse();
	}

	@Test
	@DisplayName("задачи из одной группы совместности считаются одним узлом")
	void collapsesSimultaneousGroup() {
		TaskGraph graph = new TaskGraph(List.of(simultaneous(a, b)));

		assertThat(graph.group(a)).isEqualTo(graph.group(b));
		assertThat(graph.group(a)).isNotEqualTo(graph.group(c));
	}

	@Test
	@DisplayName("группа совместности транзитивна")
	void mergesChainedSimultaneousLinks() {
		TaskGraph graph = new TaskGraph(List.of(simultaneous(a, b), simultaneous(b, c)));

		assertThat(graph.group(a)).isEqualTo(graph.group(c));
	}

	/**
	 * Ради этого случая свёртка и нужна: по исходным рёбрам противоречия не видно,
	 * каждая связь по отдельности корректна.
	 */
	@Test
	@DisplayName("связь через группу совместности замыкает цикл")
	void detectsCycleThroughSimultaneousGroup() {
		TaskGraph graph = new TaskGraph(List.of(simultaneous(a, b), sequential(a, c)));

		assertThat(graph.reachable(b, c)).isTrue();
	}

	@Test
	@DisplayName("топологический порядок ставит предшественников раньше")
	void ordersPredecessorsFirst() {
		TaskGraph graph = new TaskGraph(List.of(sequential(a, b), sequential(b, c)));

		List<UUID> order = graph.topologicalOrder();

		assertThat(order.indexOf(graph.group(a))).isLessThan(order.indexOf(graph.group(b)));
		assertThat(order.indexOf(graph.group(b))).isLessThan(order.indexOf(graph.group(c)));
	}

	@Test
	@DisplayName("цикл в графе обнаруживается при сортировке")
	void rejectsCyclicGraph() {
		TaskGraph graph = new TaskGraph(List.of(sequential(a, b), sequential(b, c), sequential(c, a)));

		assertThatThrownBy(graph::topologicalOrder)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("цикл");
	}

	@Test
	@DisplayName("связь задачи с самой собой не создаётся")
	void rejectsSelfLink() {
		assertThatThrownBy(() -> sequential(a, a)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("ненаправленная связь хранится в одном порядке независимо от ввода")
	void normalisesSimultaneousDirection() {
		TaskLink forward = simultaneous(a, b);
		TaskLink backward = simultaneous(b, a);

		assertThat(forward.getFromTaskId()).isEqualTo(backward.getFromTaskId());
		assertThat(forward.getToTaskId()).isEqualTo(backward.getToTaskId());
	}

	@Test
	@DisplayName("блокировка всегда жёсткая")
	void blockingIsAlwaysHard() {
		TaskLink link = new TaskLink(a, d, LinkType.BLOCKING, LinkHardness.SOFT);

		assertThat(link.getHardness()).isEqualTo(LinkHardness.HARD);
	}
}
