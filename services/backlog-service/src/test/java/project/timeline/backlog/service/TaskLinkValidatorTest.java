package project.timeline.backlog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import project.timeline.backlog.domain.LinkHardness;
import project.timeline.backlog.domain.LinkType;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.common.web.DomainException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskLinkValidatorTest {

	private final TaskLinkValidator validator = new TaskLinkValidator();

	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();

	private TaskLink sequential(UUID from, UUID to) {
		return new TaskLink(from, to, LinkType.SEQUENTIAL, LinkHardness.HARD);
	}

	private TaskLink simultaneous(UUID from, UUID to) {
		return new TaskLink(from, to, LinkType.SIMULTANEOUS, LinkHardness.HARD);
	}

	@Test
	@DisplayName("независимая связь принимается")
	void acceptsIndependentLink() {
		assertThatCode(() -> validator.validate(List.of(), sequential(a, b)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("повторная связь того же типа отклоняется")
	void rejectsDuplicate() {
		assertThatThrownBy(() -> validator.validate(List.of(sequential(a, b)), sequential(a, b)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("уже есть");
	}

	@Test
	@DisplayName("обратная связь между теми же задачами распознаётся как цикл")
	void rejectsReversedLinkAsCycle() {
		assertThatThrownBy(() -> validator.validate(List.of(sequential(a, b)), sequential(b, a)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("цикл");
	}

	@Test
	@DisplayName("прямой цикл отклоняется")
	void rejectsCycle() {
		List<TaskLink> existing = List.of(sequential(a, b), sequential(b, c));

		assertThatThrownBy(() -> validator.validate(existing, sequential(c, a)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("цикл");
	}

	@Test
	@DisplayName("порядок внутри группы совместности отклоняется")
	void rejectsOrderInsideSimultaneousGroup() {
		List<TaskLink> existing = List.of(simultaneous(a, b));

		assertThatThrownBy(() -> validator.validate(existing, sequential(a, b)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("выполняемые вместе");
	}

	@Test
	@DisplayName("совместность для уже упорядоченных задач отклоняется")
	void rejectsSimultaneityForOrderedTasks() {
		List<TaskLink> existing = List.of(sequential(a, b));

		assertThatThrownBy(() -> validator.validate(existing, simultaneous(a, b)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("уже упорядочены");
	}

	/**
	 * Противоречие видно только после свёртки групп: по отдельности «A вместе с B»,
	 * «A перед C» и «C перед B» корректны, но вместе плана не существует.
	 */
	@Test
	@DisplayName("связь, замыкающая цикл через группу совместности, отклоняется")
	void rejectsCycleThroughSimultaneousGroup() {
		List<TaskLink> existing = List.of(simultaneous(a, b), sequential(a, c));

		assertThatThrownBy(() -> validator.validate(existing, sequential(c, b)))
				.isInstanceOf(DomainException.class);
	}

	@Test
	@DisplayName("совместность для задач, упорядоченных транзитивно, отклоняется")
	void rejectsSimultaneityForTransitivelyOrderedTasks() {
		List<TaskLink> existing = List.of(sequential(a, b), sequential(b, c));

		assertThatThrownBy(() -> validator.validate(existing, simultaneous(a, c)))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("уже упорядочены");
	}
}
