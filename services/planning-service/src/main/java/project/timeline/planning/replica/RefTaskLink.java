package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ref_task_link")
public class RefTaskLink {

	@Id
	private UUID id;

	@Column(name = "from_task_id", nullable = false)
	private UUID fromTaskId;

	@Column(name = "to_task_id", nullable = false)
	private UUID toTaskId;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String hardness;

	protected RefTaskLink() {
	}

	public RefTaskLink(UUID id, UUID fromTaskId, UUID toTaskId, String type, String hardness) {
		this.id = id;
		this.fromTaskId = fromTaskId;
		this.toTaskId = toTaskId;
		this.type = type;
		this.hardness = hardness;
	}

	public boolean isSimultaneous() {
		return "SIMULTANEOUS".equals(type);
	}

	public boolean isBlocking() {
		return "BLOCKING".equals(type);
	}

	public boolean isHard() {
		return "HARD".equals(hardness);
	}

	/** В каскад входят только жёсткие направленные связи. */
	public boolean movesPlan() {
		return !isSimultaneous() && isHard();
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

	public String getType() {
		return type;
	}

	public String getHardness() {
		return hardness;
	}
}
