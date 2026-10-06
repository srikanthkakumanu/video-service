package com.videos.domain.port;

import java.util.Optional;

import com.videos.domain.model.PageResult;
import com.videos.domain.model.Title;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;

public interface VideoRepository {

	/** @throws com.videos.domain.exception.DuplicateVideoException if another video has the same title */
	Video save(Video video);

	Optional<Video> findById(VideoId id);

	Optional<Video> findByTitle(Title title);

	boolean exists(VideoId id);

	PageResult<Video> search(VideoSearch search);

	void delete(VideoId id);
}
