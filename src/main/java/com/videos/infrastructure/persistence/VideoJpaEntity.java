package com.videos.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Persistence shape of a video. Kept apart from the domain {@code Video}. */
@Entity
@Table(name = "video")
class VideoJpaEntity {

	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "description")
	private String description;

	@Column(name = "owner_id")
	private UUID ownerId;

	@Column(name = "completed", nullable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected VideoJpaEntity() {
	}

	VideoJpaEntity(UUID id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

	void apply(String title, String description, UUID ownerId, boolean completed, Instant updatedAt) {
		this.title = title;
		this.description = description;
		this.ownerId = ownerId;
		this.completed = completed;
		this.updatedAt = updatedAt;
	}

	UUID getId() {
		return id;
	}

	String getTitle() {
		return title;
	}

	String getDescription() {
		return description;
	}

	UUID getOwnerId() {
		return ownerId;
	}

	boolean isCompleted() {
		return completed;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}
}
