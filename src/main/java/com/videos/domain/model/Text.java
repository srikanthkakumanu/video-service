package com.videos.domain.model;

import com.videos.domain.exception.InvalidValueException;

/** Length rules shared by the text value objects. */
final class Text {

	private Text() {
	}

	static String required(String field, String value, int max) {
		if (value == null || value.isBlank()) {
			throw new InvalidValueException(field, "must not be blank");
		}
		return bounded(field, value.strip(), max);
	}

	static String optional(String field, String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return bounded(field, value.strip(), max);
	}

	private static String bounded(String field, String value, int max) {
		if (value.length() > max) {
			throw new InvalidValueException(field, "must be at most " + max + " characters");
		}
		return value;
	}
}
