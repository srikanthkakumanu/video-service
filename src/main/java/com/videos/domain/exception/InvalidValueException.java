package com.videos.domain.exception;

/** A value object or command field violates a domain constraint. */
public class InvalidValueException extends DomainException {

	private final String field;

	public InvalidValueException(String field, String message) {
		super("invalid-value", message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
