package project.timeline.schedule.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Синхронный шаг саги: просит планирование снять аллокации удаляемого спринта.
 *
 * <p>Это единственный синхронный межсервисный вызов в системе, и он намеренно
 * не лежит в пользовательском горячем пути: во всех остальных сценариях данные
 * расходятся событиями.
 *
 * <p>Токен вызывающего пробрасывается как есть — операция выполняется от имени
 * администратора, который инициировал удаление, и планирование проверяет права
 * самостоятельно.
 */
@Component
public class PlanningClient {

	private final RestClient restClient;

	public PlanningClient(RestClient.Builder builder,
			@Value("${timeline.planning.uri:http://localhost:8085}") String planningUri,
			@Value("${timeline.planning.timeout:5s}") Duration timeout) {
		var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) timeout.toMillis());
		requestFactory.setReadTimeout((int) timeout.toMillis());
		this.restClient = builder.baseUrl(planningUri).requestFactory(requestFactory).build();
	}

	/**
	 * @return сколько задач осталось без плана после снятия аллокаций
	 */
	public int releaseAllocations(UUID sprintId, UUID sagaId, String authorization) {
		ReleaseResponse response = restClient.post()
				.uri("/internal/v1/allocations/release")
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.body(new ReleaseRequest(sprintId, sagaId))
				.retrieve()
				.body(ReleaseResponse.class);
		return response == null ? 0 : response.releasedTasks();
	}

	public record ReleaseRequest(UUID sprintId, UUID sagaId) {
	}

	public record ReleaseResponse(int releasedTasks) {
	}
}
