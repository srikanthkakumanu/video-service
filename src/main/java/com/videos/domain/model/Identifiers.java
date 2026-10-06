package com.videos.domain.model;

import java.util.UUID;

import com.videos.domain.exception.InvalidValueException;

/** Parsing shared by the identifier value objects. */
final class Identifiers {

	private Identifiers() {
	}

	static UUID parse(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidValueException(field, "must not be blank");
		}
		try {
			return UUID.fromString(value.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidValueException(field, "must be a UUID");
		}
	}

	static UUID require(String field, UUID value) {
		if (value == null) {
			throw new InvalidValueException(field, "must not be null");
		}
		return value;
	}
}
