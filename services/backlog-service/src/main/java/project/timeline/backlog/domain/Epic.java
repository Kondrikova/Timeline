package project.timeline.backlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Контейнер задач: единица группировки строк на доске. */
@Entity
@Table(name = "epic")
public class Epic {

	@Id
	private UUID id;

	@Column(name = "epic_key", nullable = false, unique = true)
	private String key;

	@Column(nullable = false)
	private String name;

	private String color;

	@Column(name = "order_index", nullable = false)
	private int orderIndex;

	protected Epic() {
	}

	public Epic(String key, String name, String color, int orderIndex) {
		this.id = UUID.randomUUID();
		this.key = key;
		this.name = name;
		this.color = color;
		this.orderIndex = orderIndex;
	}

	public void update(String name, String color, int orderIndex) {
		this.name = name;
		this.color = color;
		this.orderIndex = orderIndex;
	}

	public UUID getId() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public String getColor() {
		return color;
	}

	public int getOrderIndex() {
		return orderIndex;
	}
}
