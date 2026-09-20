package project.timeline.schedule.domain;

public enum SprintState {
	PLANNED,
	ACTIVE,
	CLOSED,
	/** Промежуточное состояние саги удаления. */
	DELETING
}
