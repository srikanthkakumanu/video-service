package com.videos.domain.exception;

/** Base type for every rule the video context can refuse; {@code code} is stable and API-visible. */
public abstract class DomainException extends RuntimeException {

	private final String code;

	protected DomainException(String code, String message) {
		super(message);
		this.code = code;
	}

	protected DomainException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
