package project.timeline.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "project.timeline")
@EnableKafka
public class TimelineBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimelineBffApplication.class, args);
	}
}
