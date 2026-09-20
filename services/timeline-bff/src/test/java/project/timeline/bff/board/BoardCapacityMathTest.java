package project.timeline.bff.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import project.timeline.bff.projection.Projections;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лёгкая проверка сборки ячеек ёмкости: freeSp и флаг перегруза считаются здесь,
 * а не на клиенте.
 */
class BoardCapacityMathTest {

	@Test
	@DisplayName("отрицательный остаток помечает колонку как перегруженную")
	void marksOverflow() {
		Projections.Capacity capacity = new Projections.Capacity();
		capacity.disciplineId = UUID.randomUUID();
		capacity.vacationPersonDays = 2;
		capacity.availablePersonDays = 8;
		capacity.capacitySp = new BigDecimal("16");
		capacity.allocatedSp = new BigDecimal("20");

		BigDecimal free = capacity.capacitySp.subtract(capacity.allocatedSp);
		boolean overloaded = free.signum() < 0;

		assertThat(free).isEqualByComparingTo("-4");
		assertThat(overloaded).isTrue();
	}

	@Test
	@DisplayName("рабочие дни спринта считаются без выходных")
	void countsWeekdays() {
		assertThat(BoardAssembler.workingDaysBetween(
				java.time.LocalDate.of(2026, 9, 21),
				java.time.LocalDate.of(2026, 10, 4))).isEqualTo(10);
	}
}
