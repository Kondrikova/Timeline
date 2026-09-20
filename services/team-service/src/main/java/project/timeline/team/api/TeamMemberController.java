package project.timeline.team.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.team.api.dto.TeamDtos.DeactivateRequest;
import project.timeline.team.api.dto.TeamDtos.MemberRequest;
import project.timeline.team.api.dto.TeamDtos.MemberResponse;
import project.timeline.team.api.dto.TeamDtos.MemberUpdateRequest;
import project.timeline.team.api.dto.TeamDtos.VacationRequest;
import project.timeline.team.api.dto.TeamDtos.VacationResponse;
import project.timeline.team.service.TeamService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/team/members")
public class TeamMemberController {

	private final TeamService service;

	public TeamMemberController(TeamService service) {
		this.service = service;
	}

	@GetMapping
	public List<MemberResponse> list() {
		return service.findAll().stream().map(MemberResponse::of).toList();
	}

	@GetMapping("/{id}")
	public MemberResponse get(@PathVariable UUID id) {
		return MemberResponse.of(service.requireById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse add(@Valid @RequestBody MemberRequest request) {
		return MemberResponse.of(service.addMember(request.userId(), request.fullName(),
				request.disciplineId(), request.lead(), request.activeFrom(), request.activeTo()));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse update(@PathVariable UUID id, @Valid @RequestBody MemberUpdateRequest request) {
		return MemberResponse.of(service.updateMember(id, request.fullName(), request.disciplineId(),
				request.lead(), request.activeFrom(), request.activeTo()));
	}

	@PostMapping("/{id}/deactivation")
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateRequest request) {
		return MemberResponse.of(service.deactivateMember(id, request.lastDay()));
	}

	/**
	 * Отпуск сотрудника. ADMIN может править любой; правило владения
	 * дополнительно проверяется в сервисе для не-админов.
	 */
	@PostMapping("/{id}/vacations")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public VacationResponse addVacation(@PathVariable UUID id, @Valid @RequestBody VacationRequest request) {
		return VacationResponse.of(
				service.addVacation(id, request.startDate(), request.endDate(), request.type()));
	}

	@PutMapping("/{id}/vacations/{vacationId}")
	@PreAuthorize("hasRole('ADMIN')")
	public VacationResponse changeVacation(@PathVariable UUID id, @PathVariable UUID vacationId,
			@Valid @RequestBody VacationRequest request) {
		return VacationResponse.of(service.changeVacation(id, vacationId,
				request.startDate(), request.endDate(), request.type()));
	}

	@DeleteMapping("/{id}/vacations/{vacationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	public void cancelVacation(@PathVariable UUID id, @PathVariable UUID vacationId) {
		service.cancelVacation(id, vacationId);
	}
}
