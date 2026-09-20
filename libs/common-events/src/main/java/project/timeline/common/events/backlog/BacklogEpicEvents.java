package project.timeline.common.events.backlog;

import java.util.UUID;

/** События эпиков. */
public final class BacklogEpicEvents {

	public static final String EPIC_CREATED = "EpicCreated";
	public static final String EPIC_UPDATED = "EpicUpdated";

	public static final String TOPIC = "backlog.epic.v1";

	public record EpicPayload(UUID epicId, String key, String name, String color, int orderIndex) {
	}

	private BacklogEpicEvents() {
	}
}
