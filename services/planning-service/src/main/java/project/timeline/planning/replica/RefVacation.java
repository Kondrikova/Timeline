package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ref_vacation")
public class RefVacation {

	@Id
	private UUID id;

	@Column(name = "member_id", nullable = false)
	private UUID memberId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	protected RefVacation() {
	}

	public RefVacation(UUID id, UUID memberId, LocalDate startDate, LocalDate endDate) {
		this.id = id;
		update(memberId, startDate, endDate);
	}

	public final void update(UUID memberId, LocalDate startDate, LocalDate endDate) {
		this.memberId = memberId;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}
}
