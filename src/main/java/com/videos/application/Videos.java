package com.videos.application;

import com.videos.domain.exception.DuplicateVideoException;
import com.videos.domain.exception.OwnerNotEligibleException;
import com.videos.domain.exception.VideoNotFoundException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.UserStanding;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.UserDirectoryPort;
import com.videos.domain.port.VideoRepository;

/** Lookups and checks several use cases share. */
final class Videos {

	private Videos() {
	}

	static Video video(VideoRepository videos, VideoId id) {
		return videos.findById(id).orElseThrow(() -> new VideoNotFoundException("No video with ID " + id));
	}

	static void requireTitleFree(VideoRepository videos, VideoDetails details, VideoId self) {
		videos.findByTitle(details.title()).filter(other -> !other.id().equals(self)).ifPresent(other -> {
			throw new DuplicateVideoException("Another video is already titled '" + details.title() + "'");
		});
	}

	/** A video can only be given to a user the platform knows and who is active. */
	static void requireEligibleOwner(UserDirectoryPort directory, OwnerId owner) {
		UserStanding standing = directory.standingOf(owner);
		if (standing != UserStanding.ACTIVE) {
			throw new OwnerNotEligibleException(standing == UserStanding.UNKNOWN
					? "No user with ID " + owner
					: "User " + owner + " is not active");
		}
	}
}
