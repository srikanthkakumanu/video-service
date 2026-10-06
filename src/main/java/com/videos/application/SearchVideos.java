package com.videos.application;

import com.videos.domain.model.PageResult;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoSearch;
import com.videos.domain.port.VideoRepository;

public class SearchVideos {

	private final VideoRepository videos;

	public SearchVideos(VideoRepository videos) {
		this.videos = videos;
	}

	public PageResult<Video> handle(VideoSearch search) {
		return videos.search(search);
	}
}
