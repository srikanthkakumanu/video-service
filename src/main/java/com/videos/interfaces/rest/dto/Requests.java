package com.videos.interfaces.rest.dto;

import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Title;
import com.videos.domain.model.VideoDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies. Bean Validation rejects what is missing; the value objects enforce the rest. */
public final class Requests {

	private Requests() {
	}

	/** {@code ownerId} is only for a video manager adding a video for another user. */
	public record CreateVideo(@NotBlank @Size(max = Title.MAX_LENGTH) String title,
			@Size(max = VideoDetails.MAX_DESCRIPTION_LENGTH) String description, Boolean completed, String ownerId) {

		public VideoDetails details() {
			return new VideoDetails(new Title(title), description, Boolean.TRUE.equals(completed));
		}

		public OwnerId owner() {
			return ownerId == null || ownerId.isBlank() ? null : OwnerId.of(ownerId);
		}
	}

	public record UpdateVideo(@NotBlank @Size(max = Title.MAX_LENGTH) String title,
			@Size(max = VideoDetails.MAX_DESCRIPTION_LENGTH) String description, Boolean completed) {

		public VideoDetails details() {
			return new VideoDetails(new Title(title), description, Boolean.TRUE.equals(completed));
		}
	}

	public record TransferVideo(@NotBlank String ownerId) {
	}
}
