package project.timeline.planning.domain;

public enum ConflictType {
	/** Занято больше SP, чем даёт ёмкость роли в спринте. Предупреждение, не запрет. */
	CAPACITY_OVERFLOW(Severity.WARNING),
	/** Нарушен порядок спринтов по связи «делать после». */
	SEQUENCE_VIOLATION(Severity.ERROR),
	/** Задачи «ставим вместе» оказались в разных спринтах. */
	SIMULTANEITY_VIOLATION(Severity.ERROR),
	/** Задача запланирована, а блокирующая её задача не завершена. */
	BLOCKED_TASK(Severity.ERROR),
	/** Роль задействована в задаче, но оценка в SP не проставлена. */
	MISSING_ESTIMATE(Severity.WARNING),
	/** Задача в работе или в очереди, но не размещена ни в одном спринте. */
	UNPLANNED_TASK(Severity.WARNING),
	/** Сумма аллокаций задачи по роли расходится с оценкой. */
	OVER_ALLOCATED_TASK(Severity.WARNING);

	private final Severity severity;

	ConflictType(Severity severity) {
		this.severity = severity;
	}

	public Severity defaultSeverity() {
		return severity;
	}

	public enum Severity {
		WARNING,
		ERROR
	}
}
