package project.timeline.backlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "project.timeline")
@EntityScan(basePackages = "project.timeline")
@EnableJpaRepositories(basePackages = "project.timeline")
public class BacklogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BacklogServiceApplication.class, args);
	}
}
