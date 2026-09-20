package project.timeline.backlog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Корень агрегата «задача».
 *
 * <p>Оценки хранятся как отображение «дисциплина → SP» и входят в агрегат: набор
 * ключей одновременно задаёт перечень задействованных ролей. Появление ключа
 * означает, что роль участвует, удаление — что нет.
 *
 * <p>Значение {@code null} допустимо и означает «роль задействована, оценка ещё
 * не дана»: планирование подсветит это конфликтом, но заводить задачу без оценок
 * система не мешает.
 */
@Entity
@Table(name = "task")
public class Task {

	@Id
	private UUID id;

	@Column(name = "task_key", nullable = false, unique = true)
	private String key;

	@Column(nullable = false)
	private String title;

	@Column(name = "epic_id")
	private UUID epicId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskStatus status;

	@Column(nullable = false)
	private int priority;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskSource source;

	@Column(name = "jira_id")
	private String jiraId;

	@Column(name = "jira_synced_at")
	private Instant jiraSyncedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TaskState state;

	@OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<TaskEstimate> estimates = new ArrayList<>();

	@Version
	private Long version;

	protected Task() {
	}

	public Task(String key, String title, UUID epicId, int priority, String description, TaskSource source) {
		this.id = UUID.randomUUID();
		this.key = key;
		this.title = title;
		this.epicId = epicId;
		this.priority = priority;
		this.description = description;
		this.source = source;
		this.status = TaskStatus.TODO;
		this.state = TaskState.ACTIVE;
	}

	public void update(String title, UUID epicId, int priority, String description) {
		this.title = title;
		this.epicId = epicId;
		this.priority = priority;
		this.description = description;
	}

	public void changeStatus(TaskStatus status) {
		this.status = status;
	}

	/**
	 * Полностью заменяет набор задействованных ролей: роль, которой нет в новом
	 * наборе, больше не участвует в задаче.
	 */
	public void replaceEstimates(Map<UUID, BigDecimal> newEstimates) {
		List<TaskEstimate> replacement = newEstimates.entrySet().stream()
				.map(entry -> new TaskEstimate(this, entry.getKey(), entry.getValue()))
				.toList();
		estimates.clear();
		estimates.addAll(replacement);
	}

	public void markDeleting() {
		if (state == TaskState.DELETING) {
			throw new IllegalStateException("Удаление задачи уже выполняется");
		}
		this.state = TaskState.DELETING;
	}

	public void restore() {
		this.state = TaskState.ACTIVE;
	}

	public void linkToJira(String jiraId) {
		this.source = TaskSource.JIRA;
		this.jiraId = jiraId;
		this.jiraSyncedAt = Instant.now();
	}

	public Set<UUID> involvedDisciplines() {
		return estimates.stream().map(TaskEstimate::getDisciplineId).collect(Collectors.toSet());
	}

	public UUID getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}

	public UUID getEpicId() {
		return epicId;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public int getPriority() {
		return priority;
	}

	public String getDescription() {
		return description;
	}

	public TaskSource getSource() {
		return source;
	}

	public String getJiraId() {
		return jiraId;
	}

	public TaskState getState() {
		return state;
	}

	/**
	 * Значение может быть {@code null}, поэтому возвращается изменяемая копия в
	 * неизменяемой обёртке: {@code Map.copyOf} на {@code null}-значениях падает.
	 */
	public Map<UUID, BigDecimal> getEstimates() {
		Map<UUID, BigDecimal> copy = new LinkedHashMap<>();
		estimates.forEach(estimate -> copy.put(estimate.getDisciplineId(), estimate.getEstimateSp()));
		return java.util.Collections.unmodifiableMap(copy);
	}
}
