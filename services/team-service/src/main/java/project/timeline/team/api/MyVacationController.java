package project.timeline.team.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.team.api.dto.TeamDtos.MemberResponse;
import project.timeline.team.api.dto.TeamDtos.VacationRequest;
import project.timeline.team.api.dto.TeamDtos.VacationResponse;
import project.timeline.team.service.TeamService;

import java.util.UUID;

/**
 * Личный кабинет сотрудника.
 *
 * <p>Идентификатор сотрудника берётся из токена, а не из пути: так адрес ресурса
 * невозможно подменить, и проверка прав сводится к одному правилу в сервисе.
 */
@RestController
@RequestMapping("/api/v1/team/me")
public class MyVacationController {

	private final TeamService service;

	public MyVacationController(TeamService service) {
		this.service = service;
	}

	@GetMapping
	public MemberResponse me() {
		return MemberResponse.of(service.requireCurrent());
	}

	@PostMapping("/vacations")
	@ResponseStatus(HttpStatus.CREATED)
	public VacationResponse add(@Valid @RequestBody VacationRequest request) {
		UUID memberId = service.requireCurrent().getId();
		return VacationResponse.of(
				service.addVacation(memberId, request.startDate(), request.endDate(), request.type()));
	}

	@PutMapping("/vacations/{vacationId}")
	public VacationResponse change(@PathVariable UUID vacationId, @Valid @RequestBody VacationRequest request) {
		UUID memberId = service.requireCurrent().getId();
		return VacationResponse.of(service.changeVacation(memberId, vacationId,
				request.startDate(), request.endDate(), request.type()));
	}

	@DeleteMapping("/vacations/{vacationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable UUID vacationId) {
		service.cancelVacation(service.requireCurrent().getId(), vacationId);
	}
}
