package com.videos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.videos.domain.model.Title;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.port.VideoRepository;

/**
 * Loads the starter set of videos. Every entry goes through the domain, so seed data obeys the
 * same rules as data entered through the API. Entries whose ID is already stored are left alone,
 * which makes loading safe to repeat and never overwrites later edits.
 */
public class SeedVideos {

	public record SeedVideo(String id, String title, String description) {
	}

	private final VideoRepository videos;
	private final Clock clock;

	public SeedVideos(VideoRepository videos, Clock clock) {
		this.videos = videos;
		this.clock = clock;
	}

	/** @return how many videos were added */
	public int handle(List<SeedVideo> seedVideos) {
		Instant now = clock.instant();
		int added = 0;
		for (SeedVideo seed : seedVideos) {
			VideoId id = VideoId.of(seed.id());
			if (!videos.exists(id)) {
				VideoDetails details = new VideoDetails(new Title(seed.title()), seed.description(), false);
				Videos.requireTitleFree(videos, details, id);
				videos.save(Video.unowned(id, details, now));
				added++;
			}
		}
		return added;
	}
}
