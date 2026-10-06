package com.videos.application;

import java.time.Clock;

import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.VideoRepository;

/** Replaces the details of a video. Only its owner or a video manager may. */
public class UpdateVideo {

	private final VideoRepository videos;
	private final Clock clock;

	public UpdateVideo(VideoRepository videos, Clock clock) {
		this.videos = videos;
		this.clock = clock;
	}

	public Video handle(VideoId id, VideoDetails details, VideoActor actor) {
		Video revised = Videos.video(videos, id).revise(details, actor, clock.instant());
		Videos.requireTitleFree(videos, details, id);
		return videos.save(revised);
	}
}
