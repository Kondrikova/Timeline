package project.timeline.schedule.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.schedule.api.dto.ScheduleDtos.RescheduleRequest;
import project.timeline.schedule.api.dto.ScheduleDtos.SprintDeletedResponse;
import project.timeline.schedule.api.dto.ScheduleDtos.SprintRequest;
import project.timeline.schedule.api.dto.ScheduleDtos.SprintResponse;
import project.timeline.schedule.api.dto.ScheduleDtos.SprintStateRequest;
import project.timeline.schedule.domain.Sprint;
import project.timeline.schedule.domain.WorkingCalendar;
import project.timeline.schedule.service.ScheduleService;
import project.timeline.schedule.service.SprintDeletionSaga;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sprints")
public class SprintController {

	private final ScheduleService service;
	private final SprintDeletionSaga deletionSaga;

	public SprintController(ScheduleService service, SprintDeletionSaga deletionSaga) {
		this.service = service;
		this.deletionSaga = deletionSaga;
	}

	@GetMapping
	public List<SprintResponse> list() {
		WorkingCalendar calendar = service.workingCalendar();
		return service.findAll().stream()
				.map(sprint -> toResponse(sprint, calendar))
				.toList();
	}

	@GetMapping("/{id}")
	public SprintResponse get(@PathVariable UUID id) {
		return toResponse(service.require(id), service.workingCalendar());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public SprintResponse create(@Valid @RequestBody SprintRequest request) {
		Sprint sprint = service.create(request.number(), request.name(),
				request.startDate(), request.endDate());
		return toResponse(sprint, service.workingCalendar());
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public SprintResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
		Sprint sprint = service.reschedule(id, request.name(), request.startDate(), request.endDate());
		return toResponse(sprint, service.workingCalendar());
	}

	@PutMapping("/{id}/state")
	@PreAuthorize("hasRole('ADMIN')")
	public SprintResponse changeState(@PathVariable UUID id, @Valid @RequestBody SprintStateRequest request) {
		return toResponse(service.changeState(id, request.state()), service.workingCalendar());
	}

	/**
	 * Запускает сагу: аллокации спринта снимает планирование, и только после его
	 * подтверждения спринт исчезает. Ответ сообщает, сколько задач осталось без
	 * плана — это следствие, о котором администратор должен узнать сразу.
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public SprintDeletedResponse delete(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
		return new SprintDeletedResponse(id, deletionSaga.deleteSprint(id, authorization));
	}

	private SprintResponse toResponse(Sprint sprint, WorkingCalendar calendar) {
		return SprintResponse.of(sprint,
				calendar.workingDaysBetween(sprint.getStartDate(), sprint.getEndDate()));
	}
}
