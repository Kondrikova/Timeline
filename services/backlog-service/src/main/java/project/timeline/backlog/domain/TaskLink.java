package project.timeline.backlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Связь между задачами. Временных лагов нет: планирование ведётся в спринтах,
 * поэтому связь выражает порядок или совместность, а не задержку.
 */
@Entity
@Table(name = "task_link")
public class TaskLink {

	@Id
	private UUID id;

	@Column(name = "from_task_id", nullable = false)
	private UUID fromTaskId;

	@Column(name = "to_task_id", nullable = false)
	private UUID toTaskId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LinkType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LinkHardness hardness;

	protected TaskLink() {
	}

	public TaskLink(UUID fromTaskId, UUID toTaskId, LinkType type, LinkHardness hardness) {
		if (fromTaskId.equals(toTaskId)) {
			throw new IllegalArgumentException("Задача не может быть связана сама с собой");
		}
		this.id = UUID.randomUUID();
		this.type = type;
		// Блокировка — всегда жёсткая: помимо порядка спринтов она вводит
		// статусное правило, которое нельзя нарушить «по желанию».
		this.hardness = type == LinkType.BLOCKING ? LinkHardness.HARD : hardness;
		if (type == LinkType.SIMULTANEOUS) {
			// Отношение ненаправленное: порядок концов фиксируется, чтобы одна и
			// та же связь не сохранилась дважды в зеркальном виде.
			boolean ordered = fromTaskId.compareTo(toTaskId) <= 0;
			this.fromTaskId = ordered ? fromTaskId : toTaskId;
			this.toTaskId = ordered ? toTaskId : fromTaskId;
		}
		else {
			this.fromTaskId = fromTaskId;
			this.toTaskId = toTaskId;
		}
	}

	/**
	 * Дубликатом считается в точности та же связь. Та же пара с другим типом
	 * дубликатом не является: это противоречие, и о нём нужно сообщить по существу,
	 * а не как о повторе.
	 */
	public boolean sameAs(TaskLink other) {
		return type == other.type
				&& fromTaskId.equals(other.fromTaskId)
				&& toTaskId.equals(other.toTaskId);
	}

	public boolean touches(UUID taskId) {
		return fromTaskId.equals(taskId) || toTaskId.equals(taskId);
	}

	public UUID getId() {
		return id;
	}

	public UUID getFromTaskId() {
		return fromTaskId;
	}

	public UUID getToTaskId() {
		return toTaskId;
	}

	public LinkType getType() {
		return type;
	}

	public LinkHardness getHardness() {
		return hardness;
	}
}
