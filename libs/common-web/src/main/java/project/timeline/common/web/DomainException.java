package project.timeline.common.web;

import org.springframework.http.HttpStatus;

/**
 * Базовое исключение предметной области.
 *
 * <p>Несёт машиночитаемый код и HTTP-статус, чтобы обработчик не восстанавливал их
 * по типу исключения: соответствие «исключение — код ответа» задаётся там, где
 * ошибка возникает, и не расползается по слоям.
 */
public class DomainException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public DomainException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static DomainException notFound(String code, String message) {
		return new DomainException(HttpStatus.NOT_FOUND, code, message);
	}

	public static DomainException conflict(String code, String message) {
		return new DomainException(HttpStatus.CONFLICT, code, message);
	}

	public static DomainException badRequest(String code, String message) {
		return new DomainException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static DomainException forbidden(String code, String message) {
		return new DomainException(HttpStatus.FORBIDDEN, code, message);
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
