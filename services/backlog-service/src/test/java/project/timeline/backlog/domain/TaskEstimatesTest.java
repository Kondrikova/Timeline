package project.timeline.backlog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskEstimatesTest {

	@Test
	@DisplayName("повторная установка тех же ролей обновляет SP")
	void updatesExistingEstimateInPlace() {
		Task task = new Task("T-1", "Demo", null, 1, null, TaskSource.LOCAL);
		UUID be = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
		UUID fe = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002");

		task.replaceEstimates(Map.of(be, new BigDecimal("5"), fe, new BigDecimal("3")));

		Map<UUID, BigDecimal> next = new LinkedHashMap<>();
		next.put(be, new BigDecimal("8"));
		next.put(fe, new BigDecimal("3"));
		task.replaceEstimates(next);

		assertThat(task.getEstimates()).containsEntry(be, new BigDecimal("8"));
		assertThat(task.getEstimates()).containsEntry(fe, new BigDecimal("3"));
		assertThat(task.getEstimates()).hasSize(2);
	}

	@Test
	@DisplayName("снятие роли удаляет оценку, новая роль добавляется")
	void removesAndAddsDisciplines() {
		Task task = new Task("T-2", "Demo", null, 1, null, TaskSource.LOCAL);
		UUID be = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
		UUID fe = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002");
		UUID qa = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0003");

		Map<UUID, BigDecimal> first = new LinkedHashMap<>();
		first.put(be, new BigDecimal("5"));
		first.put(fe, new BigDecimal("2"));
		task.replaceEstimates(first);

		Map<UUID, BigDecimal> second = new LinkedHashMap<>();
		second.put(be, new BigDecimal("5"));
		second.put(qa, null);
		task.replaceEstimates(second);

		assertThat(task.getEstimates()).containsOnlyKeys(be, qa);
		assertThat(task.getEstimates().get(be)).isEqualByComparingTo("5");
		assertThat(task.getEstimates().get(qa)).isNull();
	}
}
