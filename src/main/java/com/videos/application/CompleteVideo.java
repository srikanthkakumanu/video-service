package com.videos.application;

import java.time.Clock;

import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.VideoRepository;

/** Marks a video completed. Only its owner or a video manager may. */
public class CompleteVideo {

	private final VideoRepository videos;
	private final Clock clock;

	public CompleteVideo(VideoRepository videos, Clock clock) {
		this.videos = videos;
		this.clock = clock;
	}

	public Video handle(VideoId id, VideoActor actor) {
		Video video = Videos.video(videos, id);
		Video completed = video.complete(actor, clock.instant());
		return completed == video ? video : videos.save(completed);
	}
}
