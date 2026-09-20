package project.timeline.schedule.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.schedule.api.dto.ScheduleDtos.CalendarDayResponse;
import project.timeline.schedule.api.dto.ScheduleDtos.CalendarUpdateRequest;
import project.timeline.schedule.service.ScheduleService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

	private final ScheduleService service;

	public CalendarController(ScheduleService service) {
		this.service = service;
	}

	@GetMapping("/days")
	public List<CalendarDayResponse> days(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.calendar(from, to).stream().map(CalendarDayResponse::of).toList();
	}

	@PutMapping("/days")
	@PreAuthorize("hasRole('ADMIN')")
	public List<CalendarDayResponse> update(@Valid @RequestBody CalendarUpdateRequest request) {
		return service.updateCalendar(request.days(), request.comment()).stream()
				.map(CalendarDayResponse::of).toList();
	}
}
