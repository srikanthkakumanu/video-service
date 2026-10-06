package com.videos.application;

import java.time.Clock;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.UserDirectoryPort;
import com.videos.domain.port.VideoRepository;

/** Adds a video. It belongs to whoever adds it, unless a video manager adds it for another user. */
public class CreateVideo {

	/** {@code ownerId} is null when the caller adds the video for themselves. */
	public record Command(VideoDetails details, OwnerId ownerId) {
	}

	private final VideoRepository videos;
	private final UserDirectoryPort directory;
	private final Clock clock;

	public CreateVideo(VideoRepository videos, UserDirectoryPort directory, Clock clock) {
		this.videos = videos;
		this.directory = directory;
		this.clock = clock;
	}

	public Video handle(Command command, VideoActor actor) {
		Video video = Video.create(VideoId.newId(), command.details(), command.ownerId(), actor, clock.instant());
		Videos.requireTitleFree(videos, command.details(), video.id());
		if (!video.isOwnedBy(actor.userId())) {
			Videos.requireEligibleOwner(directory, video.ownerId().orElseThrow());
		}
		return videos.save(video);
	}
}
