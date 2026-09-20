package project.timeline.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Ответ при разомкнутом размыкателе: клиент получает понятную ошибку, а не таймаут. */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

	@RequestMapping(path = "/{service}", method = { RequestMethod.GET, RequestMethod.POST })
	public ProblemDetail unavailable(@PathVariable String service) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Сервис " + service + " временно недоступен, повторите запрос позже");
		problem.setType(URI.create("https://timeline/errors/service-unavailable"));
		problem.setTitle("Service Unavailable");
		problem.setProperty("code", "SERVICE_UNAVAILABLE");
		return problem;
	}
}
