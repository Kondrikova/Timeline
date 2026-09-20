package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ref_task")
public class RefTask {

	@Id
	private UUID id;

	@Column(name = "task_key", nullable = false)
	private String key;

	@Column(nullable = false)
	private String title;

	@Column(name = "epic_id")
	private UUID epicId;

	@Column(nullable = false)
	private String status;

	protected RefTask() {
	}

	public RefTask(UUID id, String key, String title, UUID epicId, String status) {
		this.id = id;
		update(key, title, epicId, status);
	}

	public final void update(String key, String title, UUID epicId, String status) {
		this.key = key;
		this.title = title;
		this.epicId = epicId;
		this.status = status;
	}

	public boolean isDone() {
		return "DONE".equals(status);
	}

	public boolean needsPlanning() {
		return "TODO".equals(status) || "IN_PROGRESS".equals(status);
	}

	public UUID getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}

	public UUID getEpicId() {
		return epicId;
	}

	public String getStatus() {
		return status;
	}
}
