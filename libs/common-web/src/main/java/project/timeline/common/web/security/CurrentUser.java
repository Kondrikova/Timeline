package project.timeline.common.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import project.timeline.common.web.DomainException;

/** Доступ к идентификатору текущего пользователя для проверок владения ресурсом. */
public final class CurrentUser {

	public static String requireUserId() {
		return requireJwt().getSubject();
	}

	public static boolean isAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}

	/** Имя для профиля команды: given+family, иначе preferred_username, иначе sub. */
	public static String displayName() {
		Jwt jwt = requireJwt();
		String given = jwt.getClaimAsString("given_name");
		String family = jwt.getClaimAsString("family_name");
		if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
			return given + " " + family;
		}
		String preferred = jwt.getClaimAsString("preferred_username");
		if (preferred != null && !preferred.isBlank()) {
			return preferred;
		}
		String name = jwt.getClaimAsString("name");
		if (name != null && !name.isBlank()) {
			return name;
		}
		return jwt.getSubject();
	}

	public static String preferredUsername() {
		Jwt jwt = requireJwt();
		String preferred = jwt.getClaimAsString("preferred_username");
		return preferred == null || preferred.isBlank() ? jwt.getSubject() : preferred;
	}

	private static Jwt requireJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			return token.getToken();
		}
		throw DomainException.forbidden("NO_PRINCIPAL", "Запрос выполнен без токена доступа");
	}

	private CurrentUser() {
	}
}
