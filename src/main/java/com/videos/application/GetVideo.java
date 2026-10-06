package com.videos.application;

import com.videos.domain.model.Video;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.VideoRepository;

public class GetVideo {

	private final VideoRepository videos;

	public GetVideo(VideoRepository videos) {
		this.videos = videos;
	}

	public Video handle(VideoId id) {
		return Videos.video(videos, id);
	}
}
