package project.timeline.common.events.backlog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Полезные нагрузки событий, публикуемых backlog-service. */
public final class BacklogEvents {

	public static final String TASK_CREATED = "TaskCreated";
	public static final String TASK_UPDATED = "TaskUpdated";
	public static final String TASK_ESTIMATED = "TaskEstimated";
	public static final String TASK_STATUS_CHANGED = "TaskStatusChanged";
	public static final String TASK_DELETED = "TaskDeleted";
	public static final String LINK_ADDED = "TaskLinkAdded";
	public static final String LINK_REMOVED = "TaskLinkRemoved";

	public record EstimatePayload(UUID disciplineId, BigDecimal estimateSp) {
	}

	public record TaskPayload(
			UUID taskId,
			String key,
			String title,
			UUID epicId,
			String status,
			int priority,
			List<EstimatePayload> estimates) {
	}

	public record LinkPayload(
			UUID linkId,
			UUID fromTaskId,
			UUID toTaskId,
			String type,
			String hardness) {
	}

	private BacklogEvents() {
	}
}
