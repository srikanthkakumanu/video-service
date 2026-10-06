package com.videos.domain.model;

import com.videos.domain.exception.InvalidValueException;

/**
 * Whoever is asking for an operation, as far as the rules need to know: the platform user, and
 * whether auth-service has granted them management of every video.
 */
public record VideoActor(OwnerId userId, boolean managesAllVideos) {

	public VideoActor {
		if (userId == null) {
			throw new InvalidValueException("userId", "must not be null");
		}
	}

	public boolean is(OwnerId other) {
		return userId.equals(other);
	}
}
