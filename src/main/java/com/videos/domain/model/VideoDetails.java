package com.videos.domain.model;

import com.videos.domain.exception.InvalidValueException;

/** Everything about a video that its owner may change. */
public record VideoDetails(Title title, String description, boolean completed) {

	public static final int MAX_DESCRIPTION_LENGTH = 100;

	public VideoDetails {
		if (title == null) {
			throw new InvalidValueException("title", "must not be null");
		}
		description = Text.optional("description", description, MAX_DESCRIPTION_LENGTH);
	}

	public VideoDetails asCompleted() {
		return new VideoDetails(title, description, true);
	}
}
