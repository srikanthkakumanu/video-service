package com.videos.domain.exception;

/** A rule of the video context refuses the operation, whatever permissions the caller holds. */
public class OperationNotPermittedException extends DomainException {

	public OperationNotPermittedException(String message) {
		super("operation-not-permitted", message);
	}
}
