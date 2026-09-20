package project.timeline.backlog.domain;

/**
 * Пять пользовательских формулировок сводятся к трём типам: «делать после»,
 * «ставим после» и «делать до» — это одно отношение, записанное с разных концов,
 * поэтому направление канонизируется при вводе.
 */
public enum LinkType {

	/** B не раньше окончания A. */
	SEQUENTIAL(true),

	/** A и B планируются в одни и те же спринты. Отношение ненаправленное. */
	SIMULTANEOUS(false),

	/** Порядок спринтов плюс статусное правило: B нельзя вести, пока A не DONE. */
	BLOCKING(true);

	private final boolean directed;

	LinkType(boolean directed) {
		this.directed = directed;
	}

	public boolean isDirected() {
		return directed;
	}
}
