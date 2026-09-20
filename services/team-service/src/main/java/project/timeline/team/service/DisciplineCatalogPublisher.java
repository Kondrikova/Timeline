package project.timeline.team.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.timeline.team.domain.Discipline;
import project.timeline.team.repository.DisciplineRepository;

import java.util.List;

/**
 * Seed FE/BE/QA/SA пишется SQL-миграцией без outbox-событий. При старте
 * публикуем текущий справочник, чтобы planning и BFF получили все роли.
 */
@Component
public class DisciplineCatalogPublisher implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DisciplineCatalogPublisher.class);

	private final DisciplineRepository disciplines;
	private final TeamEventPublisher events;

	public DisciplineCatalogPublisher(DisciplineRepository disciplines, TeamEventPublisher events) {
		this.disciplines = disciplines;
		this.events = events;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<Discipline> all = disciplines.findAll();
		for (Discipline discipline : all) {
			events.disciplineEvent(discipline, "system");
		}
		if (!all.isEmpty()) {
			log.info("Опубликован справочник ролей: {} шт.", all.size());
		}
	}
}
