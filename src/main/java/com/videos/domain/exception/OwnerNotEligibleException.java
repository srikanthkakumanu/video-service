package com.videos.domain.exception;

/** The user a video is to be given to does not exist or is not active. */
public class OwnerNotEligibleException extends DomainException {

	public OwnerNotEligibleException(String message) {
		super("owner-not-eligible", message);
	}
}
