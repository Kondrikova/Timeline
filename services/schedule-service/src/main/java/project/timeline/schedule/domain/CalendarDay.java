package project.timeline.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Исключение из обычного правила «будни рабочие».
 *
 * <p>Хранятся только отклонения — праздники и перенесённые выходные. Полный
 * календарь на горизонт не материализуется: он выводится из дня недели и этих
 * записей.
 */
@Entity
@Table(name = "calendar_day")
public class CalendarDay {

	@Id
	@Column(name = "day")
	private LocalDate day;

	@Column(name = "is_working", nullable = false)
	private boolean working;

	@Column(name = "comment")
	private String comment;

	protected CalendarDay() {
	}

	public CalendarDay(LocalDate day, boolean working, String comment) {
		this.day = day;
		this.working = working;
		this.comment = comment;
	}

	public void change(boolean working, String comment) {
		this.working = working;
		this.comment = comment;
	}

	public LocalDate getDay() {
		return day;
	}

	public boolean isWorking() {
		return working;
	}

	public String getComment() {
		return comment;
	}
}
