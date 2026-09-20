package project.timeline.backlog.domain;

public enum TaskState {
	ACTIVE,
	/** Промежуточное состояние саги удаления задачи. */
	DELETING
}
