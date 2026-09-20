package project.timeline.team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Сканирование охватывает {@code project.timeline} целиком: кроме собственных
 * компонентов сервису нужны общие — outbox и web-обвязка.
 */
@SpringBootApplication(scanBasePackages = "project.timeline")
@EntityScan(basePackages = "project.timeline")
@EnableJpaRepositories(basePackages = "project.timeline")
public class TeamServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamServiceApplication.class, args);
	}
}
