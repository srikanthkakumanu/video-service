package com.videos.domain.model;

import java.util.UUID;

/** Identity of a video. */
public record VideoId(UUID value) {

	public VideoId {
		Identifiers.require("id", value);
	}

	public static VideoId of(String value) {
		return new VideoId(Identifiers.parse("id", value));
	}

	public static VideoId newId() {
		return new VideoId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
