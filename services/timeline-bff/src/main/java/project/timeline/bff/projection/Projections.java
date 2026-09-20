package project.timeline.bff.projection;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Промежуточные коллекции, из которых собирается {@code BoardDocument}. */
public final class Projections {

	@Document("proj_sprint")
	public static class SprintProj {
		@Id public UUID id;
		public int number;
		public String name;
		public LocalDate startDate;
		public LocalDate endDate;
		public String state;
	}

	@Document("proj_epic")
	public static class EpicProj {
		@Id public UUID id;
		public String key;
		public String name;
		public String color;
		public int orderIndex;
	}

	@Document("proj_task")
	public static class TaskProj {
		@Id public UUID id;
		public String key;
		public String title;
		public UUID epicId;
		public String status;
		public int priority;
		public List<Estimate> estimates = new ArrayList<>();
	}

	public static class Estimate {
		public UUID disciplineId;
		public BigDecimal estimateSp;
	}

	@Document("proj_link")
	public static class LinkProj {
		@Id public UUID id;
		public UUID fromTaskId;
		public UUID toTaskId;
		public String type;
		public String hardness;
	}

	@Document("proj_discipline")
	public static class DisciplineProj {
		@Id public UUID id;
		public String code;
		public String name;
	}

	@Document("proj_plan")
	public static class PlanProj {
		@Id public String id = "plan:current";
		public UUID planVersionId;
		public List<Allocation> allocations = new ArrayList<>();
		public List<Capacity> capacities = new ArrayList<>();
		public List<Conflict> conflicts = new ArrayList<>();
	}

	public static class Allocation {
		public UUID taskId;
		public UUID sprintId;
		public UUID disciplineId;
		public BigDecimal plannedSp;
	}

	public static class Capacity {
		public UUID sprintId;
		public UUID disciplineId;
		public int availablePersonDays;
		public int vacationPersonDays;
		public BigDecimal capacitySp;
		public BigDecimal allocatedSp;
	}

	public static class Conflict {
		public UUID id;
		public String type;
		public String severity;
		public UUID sprintId;
		public UUID disciplineId;
		public UUID taskId;
		public String details;
	}

	@Document("processed_event")
	public static class ProcessedEventDoc {
		@Id public String id;
		public java.time.Instant processedAt = java.time.Instant.now();

		public ProcessedEventDoc() {
		}

		public ProcessedEventDoc(String id) {
			this.id = id;
		}
	}

	private Projections() {
	}
}
