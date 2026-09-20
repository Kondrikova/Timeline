package project.timeline.team.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import project.timeline.common.web.DomainException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Каталог пользователей через Admin API Keycloak (client credentials).
 *
 * <p>Сервисный клиент {@code timeline-backend} должен иметь роли
 * {@code view-users} / {@code query-users} в {@code realm-management}.
 */
@Component
public class KeycloakDirectoryClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakDirectoryClient.class);

	private final RestClient restClient;
	private final String realm;
	private final String clientId;
	private final String clientSecret;
	private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

	public KeycloakDirectoryClient(
			RestClient.Builder builder,
			@Value("${timeline.keycloak.server-url:http://localhost:8090}") String serverUrl,
			@Value("${timeline.keycloak.realm:timeline}") String realm,
			@Value("${timeline.keycloak.client-id:timeline-backend}") String clientId,
			@Value("${timeline.keycloak.client-secret:timeline-backend-secret}") String clientSecret) {
		this.restClient = builder.baseUrl(trimTrailingSlash(serverUrl)).build();
		this.realm = realm;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	public List<KeycloakUser> listUsers() {
		try {
			KeycloakUserRepresentation[] body = restClient.get()
					.uri("/admin/realms/{realm}/users?max={max}&enabled=true", realm, 500)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve()
					.body(KeycloakUserRepresentation[].class);
			if (body == null) {
				return List.of();
			}
			return Arrays.stream(body)
					.filter(user -> user.id() != null && !user.id().isBlank())
					.filter(user -> !isServiceAccount(user))
					.map(KeycloakUserRepresentation::toUser)
					.toList();
		}
		catch (RestClientException e) {
			log.warn("Не удалось получить пользователей Keycloak: {}", e.getMessage());
			throw DomainException.badRequest("KEYCLOAK_DIRECTORY_UNAVAILABLE",
					"Каталог пользователей Keycloak недоступен");
		}
	}

	public Optional<KeycloakUser> findById(String userId) {
		try {
			KeycloakUserRepresentation body = restClient.get()
					.uri("/admin/realms/{realm}/users/{id}", realm, userId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve()
					.body(KeycloakUserRepresentation.class);
			if (body == null || isServiceAccount(body)) {
				return Optional.empty();
			}
			return Optional.of(body.toUser());
		}
		catch (RestClientException e) {
			log.warn("Пользователь Keycloak {} не найден: {}", userId, e.getMessage());
			return Optional.empty();
		}
	}

	private String accessToken() {
		CachedToken cached = tokenCache.get();
		if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
			return cached.value();
		}
		try {
			var form = new LinkedMultiValueMap<String, String>();
			form.add("grant_type", "client_credentials");
			form.add("client_id", clientId);
			form.add("client_secret", clientSecret);

			@SuppressWarnings("unchecked")
			Map<String, Object> token = restClient.post()
					.uri("/realms/{realm}/protocol/openid-connect/token", realm)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(Map.class);
			if (token == null || token.get("access_token") == null) {
				throw DomainException.badRequest("KEYCLOAK_TOKEN_FAILED",
						"Не удалось получить токен сервисного клиента Keycloak");
			}
			long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 60L;
			String value = String.valueOf(token.get("access_token"));
			tokenCache.set(new CachedToken(value, Instant.now().plusSeconds(Math.max(30, expiresIn))));
			return value;
		}
		catch (RestClientException e) {
			log.warn("Ошибка client_credentials Keycloak: {}", e.getMessage());
			throw DomainException.badRequest("KEYCLOAK_TOKEN_FAILED",
					"Не удалось получить токен сервисного клиента Keycloak");
		}
	}

	private static boolean isServiceAccount(KeycloakUserRepresentation user) {
		if (user.serviceAccountClientId() != null && !user.serviceAccountClientId().isBlank()) {
			return true;
		}
		String username = user.username();
		return username != null && username.startsWith("service-account-");
	}

	private static String trimTrailingSlash(String url) {
		if (url == null || url.isBlank()) {
			return "http://localhost:8090";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private record CachedToken(String value, Instant expiresAt) {
	}

	/**
	 * Подмножество UserRepresentation Admin API.
	 */
	private record KeycloakUserRepresentation(
			String id,
			String username,
			String firstName,
			String lastName,
			String email,
			Boolean enabled,
			String serviceAccountClientId) {

		KeycloakUser toUser() {
			return new KeycloakUser(id, username, firstName, lastName, email,
					enabled == null || enabled);
		}
	}
}
