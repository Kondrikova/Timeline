package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ref_member")
public class RefMember {

	@Id
	private UUID id;

	@Column(name = "discipline_id", nullable = false)
	private UUID disciplineId;

	@Column(name = "full_name")
	private String fullName;

	@Column(name = "active_from", nullable = false)
	private LocalDate activeFrom;

	@Column(name = "active_to")
	private LocalDate activeTo;

	protected RefMember() {
	}

	public RefMember(UUID id, UUID disciplineId, String fullName, LocalDate activeFrom, LocalDate activeTo) {
		this.id = id;
		update(disciplineId, fullName, activeFrom, activeTo);
	}

	public final void update(UUID disciplineId, String fullName, LocalDate activeFrom, LocalDate activeTo) {
		this.disciplineId = disciplineId;
		this.fullName = fullName;
		this.activeFrom = activeFrom;
		this.activeTo = activeTo;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDisciplineId() {
		return disciplineId;
	}

	public String getFullName() {
		return fullName;
	}

	public LocalDate getActiveFrom() {
		return activeFrom;
	}

	public LocalDate getActiveTo() {
		return activeTo;
	}
}
