package project.timeline.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Единая точка преобразования исключений в ответ RFC 7807.
 *
 * <p>Стектрейсы наружу не выходят; вместо них клиент получает {@code traceId},
 * по которому запрос находится в логах и трассировке.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String TYPE_PREFIX = "https://timeline/errors/";

	@ExceptionHandler(DomainException.class)
	public ProblemDetail handleDomain(DomainException e, HttpServletRequest request) {
		return problem(e.getStatus(), e.getCode(), e.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
		String detail = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail, request);
	}

	/**
	 * Нарушение ограничения БД — не «внутренняя ошибка», а конфликт: инварианты
	 * непересечения спринтов и отпусков проверяются именно там, поэтому сюда
	 * попадают гонки параллельных запросов.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
		log.warn("Нарушено ограничение целостности: {}", e.getMostSpecificCause().getMessage());
		return problem(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
				"Операция нарушает ограничение целостности данных", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав", request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Необработанная ошибка", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				"Внутренняя ошибка сервиса", request);
	}

	private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create(request.getRequestURI()));

		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("code", code);
		String traceId = RequestCorrelationFilter.currentTraceId();
		if (traceId != null) {
			properties.put("traceId", traceId);
		}
		properties.forEach(problem::setProperty);
		return problem;
	}
}
