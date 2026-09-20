package project.timeline.common.web.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Достаёт роли из claim {@code realm_access.roles}, который выдаёт Keycloak.
 *
 * <p>Остальные роли realm-а игнорируются: приложение знает только о {@code ADMIN} и
 * {@code MEMBER}, и расширение списка в Keycloak не должно молча давать права здесь.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final Set<String> KNOWN_ROLES = Set.of("ADMIN", "MEMBER");

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
	}

	@SuppressWarnings("unchecked")
	private Collection<GrantedAuthority> authorities(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaim("realm_access");
		if (realmAccess == null) {
			return List.of();
		}
		Object roles = realmAccess.get("roles");
		if (!(roles instanceof Collection<?> collection)) {
			return List.of();
		}
		return ((Collection<String>) collection).stream()
				.map(String::toUpperCase)
				.filter(KNOWN_ROLES::contains)
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
				.collect(Collectors.toSet());
	}
}
