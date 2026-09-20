package project.timeline.bff.board;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Денормализованная проекция главного экрана.
 *
 * <p>Форма документа совпадает с формой ответа API: отрисовка доски — одно чтение
 * по {@code _id}, без соединений на стороне клиента или BFF.
 */
@Document("timeline_board")
public class BoardDocument {

	public static final String CURRENT_ID = "board:current";

	@Id
	private String id = CURRENT_ID;

	private Instant updatedAt = Instant.EPOCH;
	private long revision;
	private List<SprintColumn> sprints = new ArrayList<>();
	private List<EpicRow> epics = new ArrayList<>();
	private Map<String, Integer> conflictsSummary = Map.of();

	public String getId() {
		return id;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public long getRevision() {
		return revision;
	}

	public void setRevision(long revision) {
		this.revision = revision;
	}

	public List<SprintColumn> getSprints() {
		return sprints;
	}

	public void setSprints(List<SprintColumn> sprints) {
		this.sprints = sprints;
	}

	public List<EpicRow> getEpics() {
		return epics;
	}

	public void setEpics(List<EpicRow> epics) {
		this.epics = epics;
	}

	public Map<String, Integer> getConflictsSummary() {
		return conflictsSummary;
	}

	public void setConflictsSummary(Map<String, Integer> conflictsSummary) {
		this.conflictsSummary = conflictsSummary;
	}

	public record SprintColumn(
			UUID id,
			int number,
			String name,
			LocalDate startDate,
			LocalDate endDate,
			List<CapacityCell> capacity) {
	}

	public record CapacityCell(
			UUID disciplineId,
			String disciplineCode,
			int vacationPersonDays,
			int availablePersonDays,
			BigDecimal capacitySp,
			BigDecimal allocatedSp,
			BigDecimal freeSp,
			boolean overloaded) {
	}

	public record EpicRow(UUID id, String key, String name, String color, List<TaskRow> tasks) {
	}

	public record TaskRow(
			UUID id,
			String key,
			String title,
			String status,
			List<EstimateCell> estimates,
			List<AllocationCell> cells,
			List<LinkCell> links) {
	}

	public record EstimateCell(UUID disciplineId, BigDecimal estimateSp) {
	}

	public record AllocationCell(
			UUID sprintId,
			UUID disciplineId,
			BigDecimal plannedSp,
			List<String> conflicts) {
	}

	public record LinkCell(String type, String hardness, String direction, String taskKey) {
	}
}
