package project.timeline.backlog.service;

import org.springframework.stereotype.Component;
import project.timeline.backlog.domain.LinkType;
import project.timeline.backlog.domain.TaskGraph;
import project.timeline.backlog.domain.TaskLink;
import project.timeline.common.web.DomainException;

import java.util.List;

/**
 * Проверяет, что новая связь не делает план невозможным.
 *
 * <p>Обе проверки выполняются на графе со свёрнутыми группами совместности.
 * Противоречие вида «A вместе с B» плюс «A после B» логически неразрешимо:
 * корректного плана для такой пары не существует, поэтому связь отклоняется при
 * вводе, а не всплывает в плане неустранимым конфликтом.
 */
@Component
public class TaskLinkValidator {

	public void validate(List<TaskLink> existingLinks, TaskLink candidate) {
		existingLinks.stream()
				.filter(candidate::sameAs)
				.findAny()
				.ifPresent(duplicate -> {
					throw DomainException.conflict("LINK_DUPLICATE",
							"Такая связь между задачами уже есть");
				});

		TaskGraph graph = new TaskGraph(existingLinks);

		if (candidate.getType() == LinkType.SIMULTANEOUS) {
			if (graph.reachable(candidate.getFromTaskId(), candidate.getToTaskId())
					|| graph.reachable(candidate.getToTaskId(), candidate.getFromTaskId())) {
				throw DomainException.conflict("LINK_CONTRADICTS_ORDER",
						"Задачи уже упорядочены связью, поэтому не могут идти в одном спринте");
			}
			return;
		}

		if (graph.group(candidate.getFromTaskId()).equals(graph.group(candidate.getToTaskId()))) {
			throw DomainException.conflict("LINK_INSIDE_SIMULTANEOUS_GROUP",
					"Задачи помечены как выполняемые вместе, поэтому порядок между ними невозможен");
		}

		if (graph.reachable(candidate.getToTaskId(), candidate.getFromTaskId())) {
			throw DomainException.conflict("LINK_CYCLE",
					"Связь замкнула бы граф задач в цикл");
		}
	}
}
