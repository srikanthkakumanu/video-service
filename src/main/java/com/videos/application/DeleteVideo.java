package com.videos.application;

import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.VideoRepository;

/** Removes a video. Only its owner or a video manager may. */
public class DeleteVideo {

	private final VideoRepository videos;

	public DeleteVideo(VideoRepository videos) {
		this.videos = videos;
	}

	public void handle(VideoId id, VideoActor actor) {
		Videos.video(videos, id).requireWriteAccess(actor);
		videos.delete(id);
	}
}
