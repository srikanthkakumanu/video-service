package com.videos.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.PageResult;
import com.videos.domain.model.Title;
import com.videos.domain.model.UserStanding;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import com.videos.domain.port.UserDirectoryPort;
import com.videos.domain.port.VideoRepository;

/** In-memory ports for use-case tests. */
final class InMemoryVideos implements VideoRepository {

	final Map<VideoId, Video> stored = new LinkedHashMap<>();
	int saves;

	@Override
	public Video save(Video video) {
		saves++;
		stored.put(video.id(), video);
		return video;
	}

	@Override
	public Optional<Video> findById(VideoId id) {
		return Optional.ofNullable(stored.get(id));
	}

	@Override
	public Optional<Video> findByTitle(Title title) {
		return stored.values().stream().filter(video -> video.details().title().equals(title)).findFirst();
	}

	@Override
	public boolean exists(VideoId id) {
		return stored.containsKey(id);
	}

	@Override
	public PageResult<Video> search(VideoSearch search) {
		List<Video> matches = stored.values().stream()
				.filter(video -> search.ownerId() == null || video.isOwnedBy(search.ownerId()))
				.filter(video -> search.completed() == null || video.details().completed() == search.completed())
				.sorted(Comparator.comparing(video -> video.details().title().value()))
				.toList();
		return new PageResult<>(matches, matches.size(), search.paging().page(), search.paging().size());
	}

	@Override
	public void delete(VideoId id) {
		stored.remove(id);
	}

	/** Knows the users it was told about; everyone else is unknown. Counts how often it is asked. */
	static final class Directory implements UserDirectoryPort {

		final Map<OwnerId, UserStanding> users = new LinkedHashMap<>();
		int lookups;

		@Override
		public UserStanding standingOf(OwnerId userId) {
			lookups++;
			return users.getOrDefault(userId, UserStanding.UNKNOWN);
		}
	}
}
