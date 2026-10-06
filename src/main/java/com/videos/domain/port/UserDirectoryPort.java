package com.videos.domain.port;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.UserStanding;

/** Asks the platform about a user. Users are managed elsewhere; this service only refers to them. */
public interface UserDirectoryPort {

	/** @throws com.videos.domain.exception.UserDirectoryUnavailableException if the platform cannot be asked */
	UserStanding standingOf(OwnerId userId);
}
