package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ref_sprint")
public class RefSprint {

	@Id
	private UUID id;

	@Column(nullable = false)
	private int number;

	@Column(nullable = false)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(nullable = false)
	private String state;

	protected RefSprint() {
	}

	public RefSprint(UUID id, int number, String name, LocalDate startDate, LocalDate endDate, String state) {
		this.id = id;
		update(number, name, startDate, endDate, state);
	}

	public final void update(int number, String name, LocalDate startDate, LocalDate endDate, String state) {
		this.number = number;
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
		this.state = state;
	}

	public UUID getId() {
		return id;
	}

	public int getNumber() {
		return number;
	}

	public String getName() {
		return name;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public String getState() {
		return state;
	}
}
