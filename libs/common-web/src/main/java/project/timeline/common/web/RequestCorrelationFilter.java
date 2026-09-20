package project.timeline.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Проставляет идентификатор запроса в MDC и в ответ.
 *
 * <p>Идентификатор генерируется шлюзом; сервис его принимает, а создаёт только если
 * запрос пришёл в обход шлюза. Благодаря этому логи всех сервисов и трассировка
 * сшиваются по одному значению.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "traceId";

	public static String currentTraceId() {
		return MDC.get(MDC_KEY);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String traceId = request.getHeader(HEADER);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString().replace("-", "");
		}
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}
}
