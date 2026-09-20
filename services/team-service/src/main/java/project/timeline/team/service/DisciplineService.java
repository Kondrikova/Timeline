package project.timeline.team.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.common.web.DomainException;
import project.timeline.common.web.security.CurrentUser;
import project.timeline.team.domain.Discipline;
import project.timeline.team.repository.DisciplineRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class DisciplineService {

	private final DisciplineRepository repository;
	private final TeamEventPublisher events;

	public DisciplineService(DisciplineRepository repository, TeamEventPublisher events) {
		this.repository = repository;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<Discipline> findAll() {
		return repository.findAll();
	}

	@Transactional
	public Discipline create(String code, String name, BigDecimal velocitySpPerSprint) {
		if (repository.existsByCode(code)) {
			throw DomainException.conflict("DISCIPLINE_EXISTS", "Роль с кодом " + code + " уже заведена");
		}
		Discipline discipline = repository.save(new Discipline(code, name, velocitySpPerSprint));
		events.disciplineEvent(discipline, CurrentUser.requireUserId());
		return discipline;
	}

	/**
	 * Velocity уточняется по факту закрытых спринтов, поэтому меняется отдельной
	 * операцией: это калибровка, а не редактирование справочника.
	 */
	@Transactional
	public Discipline changeVelocity(UUID id, BigDecimal velocitySpPerSprint) {
		Discipline discipline = require(id);
		discipline.setVelocitySpPerSprint(velocitySpPerSprint);
		events.disciplineEvent(discipline, CurrentUser.requireUserId());
		return discipline;
	}

	@Transactional(readOnly = true)
	public Discipline require(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> DomainException.notFound("DISCIPLINE_NOT_FOUND", "Роль не найдена: " + id));
	}
}
