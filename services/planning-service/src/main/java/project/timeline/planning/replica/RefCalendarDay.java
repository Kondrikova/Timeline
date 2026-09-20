package project.timeline.planning.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "ref_calendar_day")
public class RefCalendarDay {

	@Id
	@Column(name = "day")
	private LocalDate day;

	@Column(name = "is_working", nullable = false)
	private boolean working;

	protected RefCalendarDay() {
	}

	public RefCalendarDay(LocalDate day, boolean working) {
		this.day = day;
		this.working = working;
	}

	public void change(boolean working) {
		this.working = working;
	}

	public LocalDate getDay() {
		return day;
	}

	public boolean isWorking() {
		return working;
	}
}
