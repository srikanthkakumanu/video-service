package com.videos.interfaces.rest;

import java.net.URI;

import com.videos.application.CompleteVideo;
import com.videos.application.CreateVideo;
import com.videos.application.DeleteVideo;
import com.videos.application.GetVideo;
import com.videos.application.SearchVideos;
import com.videos.application.TransferVideo;
import com.videos.application.UpdateVideo;
import com.videos.domain.exception.InvalidValueException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Paging;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import com.videos.interfaces.rest.dto.Requests;
import com.videos.interfaces.rest.dto.Responses.PageResponse;
import com.videos.interfaces.rest.dto.Responses.VideoResponse;
import com.videos.interfaces.security.CurrentVideoActor;
import com.videos.interfaces.security.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/videos")
@Tag(name = "Videos")
class VideoController {

	private static final String ME = "me";

	private final CreateVideo createVideo;
	private final UpdateVideo updateVideo;
	private final CompleteVideo completeVideo;
	private final TransferVideo transferVideo;
	private final DeleteVideo deleteVideo;
	private final GetVideo getVideo;
	private final SearchVideos searchVideos;

	VideoController(CreateVideo createVideo, UpdateVideo updateVideo, CompleteVideo completeVideo,
			TransferVideo transferVideo, DeleteVideo deleteVideo, GetVideo getVideo, SearchVideos searchVideos) {
		this.createVideo = createVideo;
		this.updateVideo = updateVideo;
		this.completeVideo = completeVideo;
		this.transferVideo = transferVideo;
		this.deleteVideo = deleteVideo;
		this.getVideo = getVideo;
		this.searchVideos = searchVideos;
	}

	@GetMapping
	@PreAuthorize(Permissions.READ)
	@Operation(summary = "Search videos; owner=me limits the result to the caller's own videos")
	PageResponse<VideoResponse> search(@RequestParam(required = false) String title,
			@RequestParam(required = false) Boolean completed, @RequestParam(required = false) String ownerId,
			@RequestParam(required = false) String owner, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal Jwt jwt) {
		var search = new VideoSearch(title, completed, owner(owner, ownerId, jwt),
				Paging.of(page, size, sort, VideoSearch.DEFAULT_SORT, VideoSearch.SORT_FIELDS));
		return PageResponse.from(searchVideos.handle(search).map(VideoResponse::from));
	}

	@GetMapping("/{id}")
	@PreAuthorize(Permissions.READ)
	@Operation(summary = "Read a video")
	VideoResponse get(@PathVariable String id) {
		return VideoResponse.from(getVideo.handle(VideoId.of(id)));
	}

	@PostMapping
	@PreAuthorize(Permissions.WRITE)
	@Operation(summary = "Add a video; it belongs to the caller unless a video manager names another owner")
	ResponseEntity<VideoResponse> create(@Valid @RequestBody Requests.CreateVideo request,
			@AuthenticationPrincipal Jwt jwt) {
		var created = VideoResponse.from(createVideo.handle(new CreateVideo.Command(request.details(), request.owner()),
				actor(jwt)));
		return ResponseEntity.created(URI.create("/api/v1/videos/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Permissions.WRITE)
	@Operation(summary = "Replace a video's details; only its owner or a video manager may")
	VideoResponse update(@PathVariable String id, @Valid @RequestBody Requests.UpdateVideo request,
			@AuthenticationPrincipal Jwt jwt) {
		return VideoResponse.from(updateVideo.handle(VideoId.of(id), request.details(), actor(jwt)));
	}

	@PostMapping("/{id}/complete")
	@PreAuthorize(Permissions.WRITE)
	@Operation(summary = "Mark a video completed; only its owner or a video manager may")
	VideoResponse complete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
		return VideoResponse.from(completeVideo.handle(VideoId.of(id), actor(jwt)));
	}

	@PutMapping("/{id}/owner")
	@PreAuthorize(Permissions.MANAGE)
	@Operation(summary = "Give a video to another active platform user")
	VideoResponse transfer(@PathVariable String id, @Valid @RequestBody Requests.TransferVideo request,
			@AuthenticationPrincipal Jwt jwt) {
		return VideoResponse.from(transferVideo.handle(VideoId.of(id), OwnerId.of(request.ownerId()), actor(jwt)));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(Permissions.WRITE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove a video; only its owner or a video manager may")
	void delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
		deleteVideo.handle(VideoId.of(id), actor(jwt));
	}

	private static VideoActor actor(Jwt jwt) {
		return CurrentVideoActor.from(jwt);
	}

	private static OwnerId owner(String owner, String ownerId, Jwt jwt) {
		if (owner != null && !owner.isBlank()) {
			if (!ME.equals(owner)) {
				throw new InvalidValueException("owner", "must be 'me'; use ownerId for another user");
			}
			return actor(jwt).userId();
		}
		return ownerId == null || ownerId.isBlank() ? null : OwnerId.of(ownerId);
	}
}
