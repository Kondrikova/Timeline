package project.timeline.team;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * База поднимается в контейнере: миграции содержат ограничение исключения,
 * секционирование и jsonb, которых нет во встроенных СУБД. Проверять их на H2
 * бессмысленно — там они просто не выполнятся.
 *
 * <p>Если Docker недоступен, тесты пропускаются, а не падают.
 */
@SpringBootTest(properties = {
		"timeline.outbox.enabled=false",
		"spring.kafka.bootstrap-servers=localhost:59092"
})
public abstract class AbstractPostgresTest {

	private static final PostgreSQLContainer<?> POSTGRES;

	static {
		POSTGRES = dockerAvailable()
				? new PostgreSQLContainer<>("postgres:17-alpine").withReuse(true)
				: null;
		if (POSTGRES != null) {
			POSTGRES.start();
		}
	}

	/**
	 * Проверка выполняется до создания тестового экземпляра, поэтому контекст
	 * Spring не поднимается и тест не падает на отсутствующей базе.
	 */
	@BeforeAll
	static void requireDocker() {
		Assumptions.assumeTrue(dockerAvailable(),
				"Docker недоступен: интеграционные тесты на Testcontainers пропущены");
	}

	public static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		}
		catch (Throwable e) {
			return false;
		}
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		if (POSTGRES == null) {
			return;
		}
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
