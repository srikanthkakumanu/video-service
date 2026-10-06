package com.videos.domain.exception;

/** The platform could not be asked about a user. */
public class UserDirectoryUnavailableException extends DomainException {

	public UserDirectoryUnavailableException(String message, Throwable cause) {
		super("user-directory-unavailable", message, cause);
	}
}
