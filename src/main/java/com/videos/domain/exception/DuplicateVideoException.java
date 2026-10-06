package com.videos.domain.exception;

/** Another video already has this title. */
public class DuplicateVideoException extends DomainException {

	public DuplicateVideoException(String message) {
		super("duplicate-video", message);
	}
}
