package project.timeline.planning.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CascadePlannerTest {

	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();

	@Test
	@DisplayName("перенос задачи толкает последователя ровно на нужный сдвиг")
	void pushesSuccessorJustEnough() {
		PlanGraph graph = new PlanGraph(Set.of(a, b), List.of(
				new PlanGraph.Link(a, b, PlanGraph.Kind.ORDERED)));
		Map<UUID, CascadePlanner.Span> spans = Map.of(
				a, new CascadePlanner.Span(0, 0),
				b, new CascadePlanner.Span(1, 1));

		Map<UUID, Integer> shifts = new CascadePlanner(graph, spans).plan(a, 2);

		assertThat(shifts.get(a)).isEqualTo(2);
		assertThat(shifts.get(b)).isEqualTo(2);
	}

	@Test
	@DisplayName("группа совместности двигается целиком")
	void movesSimultaneousGroupTogether() {
		PlanGraph graph = new PlanGraph(Set.of(a, b, c), List.of(
				new PlanGraph.Link(a, b, PlanGraph.Kind.SIMULTANEOUS),
				new PlanGraph.Link(a, c, PlanGraph.Kind.ORDERED)));
		Map<UUID, CascadePlanner.Span> spans = Map.of(
				a, new CascadePlanner.Span(0, 0),
				b, new CascadePlanner.Span(0, 0),
				c, new CascadePlanner.Span(1, 1));

		Map<UUID, Integer> shifts = new CascadePlanner(graph, spans).plan(a, 2);

		assertThat(shifts.get(a)).isEqualTo(2);
		assertThat(shifts.get(b)).isEqualTo(2);
		assertThat(shifts.get(c)).isEqualTo(2);
	}

	@Test
	@DisplayName("уже достаточный зазор не порождает лишнего сдвига")
	void doesNotOvershiftWhenGapExists() {
		PlanGraph graph = new PlanGraph(Set.of(a, b), List.of(
				new PlanGraph.Link(a, b, PlanGraph.Kind.ORDERED)));
		Map<UUID, CascadePlanner.Span> spans = Map.of(
				a, new CascadePlanner.Span(0, 0),
				b, new CascadePlanner.Span(3, 3));

		Map<UUID, Integer> shifts = new CascadePlanner(graph, spans).plan(a, 1);

		assertThat(shifts.get(a)).isEqualTo(1);
		assertThat(shifts.containsKey(b)).isFalse();
	}

	@Test
	@DisplayName("транзитивная цепочка сдвигается целиком")
	void shiftsTransitiveChain() {
		PlanGraph graph = new PlanGraph(Set.of(a, b, c), List.of(
				new PlanGraph.Link(a, b, PlanGraph.Kind.ORDERED),
				new PlanGraph.Link(b, c, PlanGraph.Kind.ORDERED)));
		Map<UUID, CascadePlanner.Span> spans = Map.of(
				a, new CascadePlanner.Span(0, 0),
				b, new CascadePlanner.Span(1, 1),
				c, new CascadePlanner.Span(2, 2));

		Map<UUID, Integer> shifts = new CascadePlanner(graph, spans).plan(a, 3);

		assertThat(shifts.get(a)).isEqualTo(3);
		assertThat(shifts.get(b)).isEqualTo(3);
		assertThat(shifts.get(c)).isEqualTo(3);
	}
}
