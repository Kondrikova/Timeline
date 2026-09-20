package project.timeline.common.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import project.timeline.common.web.DomainException;

/** Доступ к идентификатору текущего пользователя для проверок владения ресурсом. */
public final class CurrentUser {

	public static String requireUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			Jwt jwt = token.getToken();
			return jwt.getSubject();
		}
		throw DomainException.forbidden("NO_PRINCIPAL", "Запрос выполнен без токена доступа");
	}

	public static boolean isAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}

	private CurrentUser() {
	}
}
