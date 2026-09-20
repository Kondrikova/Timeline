package project.timeline.team.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.timeline.team.api.dto.TeamDtos.DirectoryAssignRequest;
import project.timeline.team.api.dto.TeamDtos.DirectoryUserResponse;
import project.timeline.team.api.dto.TeamDtos.MemberResponse;
import project.timeline.team.service.TeamService;

import java.util.List;

/**
 * Каталог зарегистрированных в Keycloak пользователей для назначения ролей.
 *
 * <p>Админ видит всех людей realm и назначает дисциплину (FE/BE/QA/SA) без
 * ручного ввода Keycloak sub.
 */
@RestController
@RequestMapping("/api/v1/team/directory")
public class DirectoryController {

	private final TeamService service;

	public DirectoryController(TeamService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<DirectoryUserResponse> list() {
		return service.directory();
	}

	@PutMapping("/{userId}/discipline")
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse assign(@PathVariable String userId,
			@Valid @RequestBody DirectoryAssignRequest request) {
		return MemberResponse.of(
				service.assignDirectoryDiscipline(userId, request.disciplineId(), request.lead()));
	}
}
