package project.timeline.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Ограничение частоты считается по пользователю, а не по IP: за одним адресом
 * может стоять вся команда, и лимит на адрес наказывал бы всех сразу.
 */
@Configuration
public class RateLimiterConfig {

	@Bean
	KeyResolver userKeyResolver() {
		return exchange -> ReactiveSecurityContextHolder.getContext()
				.map(context -> context.getAuthentication().getName())
				.defaultIfEmpty(remoteAddress(exchange));
	}

	private static String remoteAddress(org.springframework.web.server.ServerWebExchange exchange) {
		return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
				.map(address -> address.getAddress().getHostAddress())
				.orElse("unknown");
	}
}
