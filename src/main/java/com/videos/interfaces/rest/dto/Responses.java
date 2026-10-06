package com.videos.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.PageResult;
import com.videos.domain.model.Video;

/** Response bodies. */
public final class Responses {

	private Responses() {
	}

	/** {@code ownerId} is the platform user ID of the owner, or null for a video of the starter set. */
	public record VideoResponse(String id, String title, String description, String ownerId, boolean completed,
			Instant createdAt, Instant updatedAt) {

		public static VideoResponse from(Video video) {
			return new VideoResponse(video.id().toString(), video.details().title().value(),
					video.details().description(), video.ownerId().map(OwnerId::toString).orElse(null),
					video.details().completed(), video.createdAt(), video.updatedAt());
		}
	}

	public record PageResponse<T>(List<T> items, long total, int page, int size) {

		public static <T> PageResponse<T> from(PageResult<T> result) {
			return new PageResponse<>(result.items(), result.total(), result.page(), result.size());
		}
	}
}
