package project.timeline.team.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import project.timeline.team.domain.Discipline;
import project.timeline.team.domain.TeamMember;
import project.timeline.team.domain.Vacation;
import project.timeline.team.domain.VacationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {

	public record DisciplineRequest(
			@NotBlank String code,
			@NotBlank String name,
			@NotNull @Positive BigDecimal velocitySpPerSprint) {
	}

	public record VelocityRequest(@NotNull @Positive BigDecimal velocitySpPerSprint) {
	}

	public record DisciplineResponse(UUID id, String code, String name, BigDecimal velocitySpPerSprint) {

		public static DisciplineResponse of(Discipline discipline) {
			return new DisciplineResponse(discipline.getId(), discipline.getCode(),
					discipline.getName(), discipline.getVelocitySpPerSprint());
		}
	}

	public record MemberRequest(
			@NotBlank String userId,
			@NotBlank String fullName,
			@NotNull UUID disciplineId,
			boolean lead,
			@NotNull LocalDate activeFrom,
			LocalDate activeTo) {
	}

	public record MemberUpdateRequest(
			@NotBlank String fullName,
			@NotNull UUID disciplineId,
			boolean lead,
			@NotNull LocalDate activeFrom,
			LocalDate activeTo) {
	}

	public record DeactivateRequest(@NotNull LocalDate lastDay) {
	}

	public record MemberResponse(
			UUID id,
			String userId,
			String fullName,
			UUID disciplineId,
			String disciplineCode,
			boolean lead,
			LocalDate activeFrom,
			LocalDate activeTo,
			List<VacationResponse> vacations) {

		public static MemberResponse of(TeamMember member) {
			return new MemberResponse(
					member.getId(),
					member.getUserId(),
					member.getFullName(),
					member.getDiscipline().getId(),
					member.getDiscipline().getCode(),
					member.isLead(),
					member.getActiveFrom(),
					member.getActiveTo(),
					member.getVacations().stream().map(VacationResponse::of).toList());
		}
	}

	public record VacationRequest(
			@NotNull LocalDate startDate,
			@NotNull LocalDate endDate,
			@NotNull VacationType type) {
	}

	public record VacationResponse(UUID id, LocalDate startDate, LocalDate endDate, VacationType type) {

		public static VacationResponse of(Vacation vacation) {
			return new VacationResponse(vacation.getId(), vacation.getStartDate(),
					vacation.getEndDate(), vacation.getType());
		}
	}

	private TeamDtos() {
	}
}
