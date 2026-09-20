package project.timeline.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "project.timeline")
@EntityScan(basePackages = "project.timeline")
@EnableJpaRepositories(basePackages = "project.timeline")
@EnableKafka
public class PlanningServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlanningServiceApplication.class, args);
	}
}
