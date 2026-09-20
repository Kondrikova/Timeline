package project.timeline.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisciplineTest {

	@Test
	@DisplayName("velocity нормируется на рабочий день эталонного спринта")
	void normalizesVelocityToWorkingDay() {
		Discipline discipline = new Discipline("BACKEND", "Разработка", new BigDecimal("20"));

		assertThat(discipline.velocitySpPerDay(10)).isEqualByComparingTo("2");
	}

	@Test
	@DisplayName("неположительная velocity отклоняется")
	void rejectsNonPositiveVelocity() {
		assertThatThrownBy(() -> new Discipline("QA", "Тестирование", BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
