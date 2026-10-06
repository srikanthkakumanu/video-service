package com.videos.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.videos.domain.exception.OperationNotPermittedException;

/**
 * A video. A video with an owner is changed only by that owner or by someone who manages every
 * video; a video without an owner belongs to the service itself (the starter set) and is changed
 * only by the latter.
 */
public final class Video {

	private final VideoId id;
	private final VideoDetails details;
	private final OwnerId ownerId;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Video(VideoId id, VideoDetails details, OwnerId ownerId, Instant createdAt, Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.details = Objects.requireNonNull(details, "details");
		this.ownerId = ownerId;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
	}

	/** A video added by a user. It belongs to them unless a manager adds it for someone else. */
	public static Video create(VideoId id, VideoDetails details, OwnerId requestedOwner, VideoActor actor, Instant now) {
		OwnerId owner = requestedOwner == null ? actor.userId() : requestedOwner;
		if (!actor.is(owner) && !actor.managesAllVideos()) {
			throw new OperationNotPermittedException("Only a video manager may add a video for someone else");
		}
		return new Video(id, details, owner, now, now);
	}

	/** A video that belongs to the service itself, such as seed data. */
	public static Video unowned(VideoId id, VideoDetails details, Instant now) {
		return new Video(id, details, null, now, now);
	}

	/** Rebuilds a video from storage. */
	public static Video rehydrate(VideoId id, VideoDetails details, OwnerId ownerId, Instant createdAt, Instant updatedAt) {
		return new Video(id, details, ownerId, createdAt, updatedAt);
	}

	public Video revise(VideoDetails details, VideoActor actor, Instant now) {
		requireWriteAccess(actor);
		return new Video(id, details, ownerId, createdAt, now);
	}

	/** Marks the video completed. Completing a completed video changes nothing. */
	public Video complete(VideoActor actor, Instant now) {
		requireWriteAccess(actor);
		return details.completed() ? this : new Video(id, details.asCompleted(), ownerId, createdAt, now);
	}

	public Video transferTo(OwnerId newOwner, VideoActor actor, Instant now) {
		Objects.requireNonNull(newOwner, "newOwner");
		if (!actor.managesAllVideos()) {
			throw new OperationNotPermittedException("Only a video manager may transfer a video");
		}
		return new Video(id, details, newOwner, createdAt, now);
	}

	public void requireWriteAccess(VideoActor actor) {
		if (!actor.managesAllVideos() && !isOwnedBy(actor.userId())) {
			throw new OperationNotPermittedException("A video may only be changed by its owner or a video manager");
		}
	}

	public boolean isOwnedBy(OwnerId user) {
		return ownerId != null && ownerId.equals(user);
	}

	public VideoId id() {
		return id;
	}

	public VideoDetails details() {
		return details;
	}

	public Optional<OwnerId> ownerId() {
		return Optional.ofNullable(ownerId);
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
