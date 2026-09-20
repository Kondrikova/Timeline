package project.timeline.gateway;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Присваивает запросу идентификатор и пробрасывает его дальше.
 *
 * <p>Заголовок задаётся один раз на входе в систему: все сервисы, логи и трассировка
 * ниже по цепочке используют одно и то же значение.
 */
@Component
public class RequestIdFilter implements WebFilter, Ordered {

	static final String HEADER = "X-Request-Id";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString().replace("-", "");
		}
		ServerHttpRequest request = exchange.getRequest().mutate()
				.header(HEADER, requestId)
				.build();
		exchange.getResponse().getHeaders().set(HEADER, requestId);
		return chain.filter(exchange.mutate().request(request).build());
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
