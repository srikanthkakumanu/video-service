package com.videos.domain.exception;

/** No video has the requested ID. */
public class VideoNotFoundException extends DomainException {

	public VideoNotFoundException(String message) {
		super("video-not-found", message);
	}
}
