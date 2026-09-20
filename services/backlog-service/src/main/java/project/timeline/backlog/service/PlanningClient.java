package project.timeline.backlog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Синхронный шаг саги удаления задачи: просит планирование снять аллокации.
 *
 * <p>Единственный синхронный межсервисный вызов из backlog-service. Токен
 * вызывающего пробрасывается как есть — планирование проверяет права само.
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
	 * @return сколько аллокаций снято с задачи
	 */
	public int releaseTaskAllocations(UUID taskId, UUID sagaId, String authorization) {
		ReleaseResponse response = restClient.post()
				.uri("/internal/v1/allocations/release-task")
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.body(new ReleaseTaskRequest(taskId, sagaId))
				.retrieve()
				.body(ReleaseResponse.class);
		return response == null ? 0 : response.releasedAllocations();
	}

	public record ReleaseTaskRequest(UUID taskId, UUID sagaId) {
	}

	public record ReleaseResponse(int releasedAllocations) {
	}
}
