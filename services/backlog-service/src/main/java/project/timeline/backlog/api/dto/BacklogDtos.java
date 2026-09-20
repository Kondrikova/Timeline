package project.timeline.backlog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import project.timeline.backlog.domain.Epic;
import project.timeline.backlog.domain.LinkHardness;
import project.timeline.backlog.domain.LinkType;
import project.timeline.backlog.domain.Task;
import project.timeline.backlog.domain.TaskComment;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.backlog.domain.TaskSource;
import project.timeline.backlog.domain.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class BacklogDtos {

	public record EpicRequest(
			@NotBlank String key,
			@NotBlank String name,
			String color,
			int orderIndex) {
	}

	public record EpicUpdateRequest(
			@NotBlank String name,
			String color,
			int orderIndex) {
	}

	public record EpicResponse(UUID id, String key, String name, String color, int orderIndex) {

		public static EpicResponse of(Epic epic) {
			return new EpicResponse(epic.getId(), epic.getKey(), epic.getName(),
					epic.getColor(), epic.getOrderIndex());
		}
	}

	public record TaskRequest(
			@NotBlank String key,
			@NotBlank String title,
			UUID epicId,
			int priority,
			String description) {
	}

	public record TaskUpdateRequest(
			@NotBlank String title,
			UUID epicId,
			int priority,
			String description) {
	}

	public record StatusRequest(@NotNull TaskStatus status) {
	}

	/**
	 * Ключи отображения — задействованные роли, значение {@code null} допустимо и
	 * означает «роль участвует, оценка ещё не дана».
	 */
	public record EstimatesRequest(@NotNull Map<UUID, BigDecimal> estimates) {
	}

	public record TaskResponse(
			UUID id,
			String key,
			String title,
			UUID epicId,
			TaskStatus status,
			int priority,
			String description,
			TaskSource source,
			Map<UUID, BigDecimal> estimates) {

		public static TaskResponse of(Task task) {
			return new TaskResponse(task.getId(), task.getKey(), task.getTitle(), task.getEpicId(),
					task.getStatus(), task.getPriority(), task.getDescription(), task.getSource(),
					task.getEstimates());
		}
	}

	public record LinkRequest(
			@NotNull UUID toTaskId,
			@NotNull LinkType type,
			LinkHardness hardness) {
	}

	public record LinkResponse(UUID id, UUID fromTaskId, UUID toTaskId, LinkType type, LinkHardness hardness) {

		public static LinkResponse of(TaskLink link) {
			return new LinkResponse(link.getId(), link.getFromTaskId(), link.getToTaskId(),
					link.getType(), link.getHardness());
		}
	}

	public record CommentRequest(@NotBlank String body) {
	}

	public record CommentResponse(UUID id, String authorUserId, String body, Instant createdAt) {

		public static CommentResponse of(TaskComment comment) {
			return new CommentResponse(comment.getId(), comment.getAuthorUserId(),
					comment.getBody(), comment.getCreatedAt());
		}
	}

	public record TaskDeletedResponse(UUID taskId, int releasedAllocations) {
	}

	private BacklogDtos() {
	}
}
