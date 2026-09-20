package project.timeline.backlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comment")
public class TaskComment {

	@Id
	private UUID id;

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "author_user_id", nullable = false)
	private String authorUserId;

	@Column(columnDefinition = "text", nullable = false)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TaskComment() {
	}

	public TaskComment(UUID taskId, String authorUserId, String body) {
		this.id = UUID.randomUUID();
		this.taskId = taskId;
		this.authorUserId = authorUserId;
		this.body = body;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public String getAuthorUserId() {
		return authorUserId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
