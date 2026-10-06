package com.videos.domain.model;

/** The title of a video. Titles are short, and no two videos share one. */
public record Title(String value) {

	public static final int MAX_LENGTH = 30;

	public Title {
		value = Text.required("title", value, MAX_LENGTH);
	}

	@Override
	public String toString() {
		return value;
	}
}
