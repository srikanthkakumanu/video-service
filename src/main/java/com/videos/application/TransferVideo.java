package com.videos.application;

import java.time.Clock;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.UserDirectoryPort;
import com.videos.domain.port.VideoRepository;

/** Gives a video to another user. Only a video manager may, and only to an active platform user. */
public class TransferVideo {

	private final VideoRepository videos;
	private final UserDirectoryPort directory;
	private final Clock clock;

	public TransferVideo(VideoRepository videos, UserDirectoryPort directory, Clock clock) {
		this.videos = videos;
		this.directory = directory;
		this.clock = clock;
	}

	public Video handle(VideoId id, OwnerId newOwner, VideoActor actor) {
		Video transferred = Videos.video(videos, id).transferTo(newOwner, actor, clock.instant());
		Videos.requireEligibleOwner(directory, newOwner);
		return videos.save(transferred);
	}
}
