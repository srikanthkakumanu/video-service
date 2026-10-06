package com.videos.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.platform.security.autoconfigure.PlatformSecurityAutoConfiguration;
import com.platform.security.jwt.PlatformAuthoritiesConverter;
import com.videos.application.CompleteVideo;
import com.videos.application.CreateVideo;
import com.videos.application.DeleteVideo;
import com.videos.application.GetVideo;
import com.videos.application.SearchVideos;
import com.videos.application.TransferVideo;
import com.videos.application.UpdateVideo;
import com.videos.domain.exception.DuplicateVideoException;
import com.videos.domain.exception.OperationNotPermittedException;
import com.videos.domain.exception.OwnerNotEligibleException;
import com.videos.domain.exception.UserDirectoryUnavailableException;
import com.videos.domain.exception.VideoNotFoundException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.PageResult;
import com.videos.domain.model.Paging;
import com.videos.domain.model.Title;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import com.videos.interfaces.rest.error.ProblemResponses;
import com.videos.interfaces.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validation, error mapping and authorization rules of the video API, with use cases mocked. */
@WebMvcTest(properties = {
		"platform.security.jwt.issuer-uri=http://localhost:8080/realms/platform",
		"platform.security.jwt.jwk-set-uri=http://localhost:1/certs",
		"platform.security.jwt.audience=video-service" })
@ImportAutoConfiguration(PlatformSecurityAutoConfiguration.class)
@Import({ SecurityConfiguration.class, ProblemResponses.class })
class VideoApiTest {

	private static final String ALICE = "7c1f0e9a-3b5d-4f6a-8b7c-9d0e1f2a3b4c";
	private static final String BOB = "11111111-2222-3333-4444-555555555555";
	private static final String VIDEO = "aaaaaaaa-0000-4000-8000-000000000001";
	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final String VALID_VIDEO = """
			{"title": "Intro", "description": "A first look."}""";
	private static final String TRANSFER = "{\"ownerId\": \"" + BOB + "\"}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean private CreateVideo createVideo;
	@MockitoBean private UpdateVideo updateVideo;
	@MockitoBean private CompleteVideo completeVideo;
	@MockitoBean private TransferVideo transferVideo;
	@MockitoBean private DeleteVideo deleteVideo;
	@MockitoBean private GetVideo getVideo;
	@MockitoBean private SearchVideos searchVideos;

	// --- login is required for everything

	@Test
	void withoutATokenEveryEndpointIsUnauthorized() throws Exception {
		mvc.perform(get("/api/v1/videos")).andExpect(status().isUnauthorized()).andExpect(problem("unauthorized"));
		mvc.perform(get("/api/v1/videos/" + VIDEO)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/videos").contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isUnauthorized());
		mvc.perform(put("/api/v1/videos/" + VIDEO).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/videos/" + VIDEO + "/complete")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/v1/videos/" + VIDEO)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/videos/ping")).andExpect(status().isUnauthorized());
		verifyNoInteractions(searchVideos, getVideo, createVideo, updateVideo, completeVideo, deleteVideo);
	}

	// --- being logged in is not enough: a video permission is needed

	@Test
	void aLoggedInUserWithoutAVideoRoleCanDoNothingEvenWithBookPermissions() throws Exception {
		var plainUser = token(ALICE);
		var bookManager = token(ALICE, "books:read", "books:write", "books:manage", "authors:manage");

		for (var caller : List.of(plainUser, bookManager)) {
			mvc.perform(get("/api/v1/videos").with(caller)).andExpect(status().isForbidden()).andExpect(problem("forbidden"));
			mvc.perform(get("/api/v1/videos/" + VIDEO).with(caller)).andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/videos").with(caller).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
					.andExpect(status().isForbidden());
			mvc.perform(delete("/api/v1/videos/" + VIDEO).with(caller)).andExpect(status().isForbidden());
		}
		verifyNoInteractions(searchVideos, getVideo, createVideo, deleteVideo);
	}

	@Test
	void aReaderReadsButCannotWrite() throws Exception {
		var reader = token(ALICE, "videos:read");
		given(getVideo.handle(VideoId.of(VIDEO))).willReturn(video(ALICE, false));

		mvc.perform(get("/api/v1/videos/" + VIDEO).with(reader)).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Intro")).andExpect(jsonPath("$.ownerId").value(ALICE))
				.andExpect(jsonPath("$.userName").doesNotExist());

		mvc.perform(post("/api/v1/videos").with(reader).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isForbidden()).andExpect(problem("forbidden"));
		mvc.perform(put("/api/v1/videos/" + VIDEO).with(reader).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/videos/" + VIDEO + "/complete").with(reader)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/videos/" + VIDEO).with(reader)).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/videos/" + VIDEO + "/owner").with(reader).contentType(MediaType.APPLICATION_JSON)
				.content(TRANSFER)).andExpect(status().isForbidden());
		verifyNoInteractions(createVideo, updateVideo, completeVideo, deleteVideo, transferVideo);
	}

	@Test
	void anEditorCannotTransferAndARoleNameAloneGrantsNothing() throws Exception {
		mvc.perform(put("/api/v1/videos/" + VIDEO + "/owner").with(token(ALICE, "videos:read", "videos:write"))
				.contentType(MediaType.APPLICATION_JSON).content(TRANSFER)).andExpect(status().isForbidden());

		JwtRequestPostProcessor roleOnly = jwt().jwt(token -> token.subject(ALICE)
				.claim("realm_access", Map.of("roles", List.of("VIDEO_MANAGER", "ADMIN", "MANAGER"))))
				.authorities(new PlatformAuthoritiesConverter());
		mvc.perform(get("/api/v1/videos").with(roleOnly)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/videos/" + VIDEO).with(roleOnly)).andExpect(status().isForbidden());
		verifyNoInteractions(transferVideo, searchVideos, deleteVideo);
	}

	// --- videos

	@Test
	void anEditorAddsAVideoForThemselves() throws Exception {
		given(createVideo.handle(any(), any())).willReturn(video(ALICE, false));

		mvc.perform(post("/api/v1/videos").with(token(ALICE, "videos:write")).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_VIDEO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/videos/" + VIDEO))
				.andExpect(jsonPath("$.id").value(VIDEO)).andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.ownerId").value(ALICE));

		var command = ArgumentCaptor.forClass(CreateVideo.Command.class);
		var actor = ArgumentCaptor.forClass(VideoActor.class);
		verify(createVideo).handle(command.capture(), actor.capture());
		assertThat(command.getValue().ownerId()).isNull();
		assertThat(command.getValue().details()).isEqualTo(new VideoDetails(new Title("Intro"), "A first look.", false));
		assertThat(actor.getValue()).isEqualTo(new VideoActor(OwnerId.of(ALICE), false));
	}

	@Test
	void theCallerIsTakenFromTheTokenAndManagementFromItsPermissions() throws Exception {
		given(createVideo.handle(any(), any())).willReturn(video(BOB, true));

		mvc.perform(post("/api/v1/videos").with(token(ALICE, "videos:write", "videos:manage"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Intro\", \"completed\": true, \"ownerId\": \"" + BOB + "\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value(BOB));

		var command = ArgumentCaptor.forClass(CreateVideo.Command.class);
		var actor = ArgumentCaptor.forClass(VideoActor.class);
		verify(createVideo).handle(command.capture(), actor.capture());
		assertThat(command.getValue().ownerId()).isEqualTo(OwnerId.of(BOB));
		assertThat(command.getValue().details().completed()).isTrue();
		assertThat(actor.getValue()).isEqualTo(new VideoActor(OwnerId.of(ALICE), true));
	}

	@Test
	void aVideoRequestIsValidated() throws Exception {
		var editor = token(ALICE, "videos:write");

		mvc.perform(post("/api/v1/videos").with(editor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \" \"}"))
				.andExpect(status().isBadRequest()).andExpect(problem("invalid-value"))
				.andExpect(jsonPath("$.errors[0].field").value("title"));
		mvc.perform(post("/api/v1/videos").with(editor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"" + "x".repeat(31) + "\", \"description\": \"" + "y".repeat(101) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("title", "description")));
		mvc.perform(post("/api/v1/videos").with(editor).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Intro\", \"ownerId\": \"nope\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("ownerId"));
		mvc.perform(post("/api/v1/videos").with(editor).contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest()).andExpect(problem("invalid-value"));
		mvc.perform(get("/api/v1/videos/not-a-uuid").with(token(ALICE, "videos:read")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("id"));
		verifyNoInteractions(createVideo, getVideo);
	}

	@Test
	void updateCompleteDeleteAndTransferPassTheCallerToTheUseCases() throws Exception {
		given(updateVideo.handle(eq(VideoId.of(VIDEO)), any(), any())).willReturn(video(ALICE, false));
		given(completeVideo.handle(eq(VideoId.of(VIDEO)), any())).willReturn(video(ALICE, true));
		given(transferVideo.handle(eq(VideoId.of(VIDEO)), eq(OwnerId.of(BOB)), any())).willReturn(video(BOB, false));
		var editor = token(ALICE, "videos:write");

		mvc.perform(put("/api/v1/videos/" + VIDEO).with(editor).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_VIDEO)).andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(ALICE));
		mvc.perform(post("/api/v1/videos/" + VIDEO + "/complete").with(editor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true));
		mvc.perform(delete("/api/v1/videos/" + VIDEO).with(editor)).andExpect(status().isNoContent())
				.andExpect(content().string(""));
		mvc.perform(put("/api/v1/videos/" + VIDEO + "/owner").with(token(ALICE, "videos:manage"))
				.contentType(MediaType.APPLICATION_JSON).content(TRANSFER))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(BOB));

		var ordinary = new VideoActor(OwnerId.of(ALICE), false);
		verify(completeVideo).handle(VideoId.of(VIDEO), ordinary);
		verify(deleteVideo).handle(VideoId.of(VIDEO), ordinary);
		verify(transferVideo).handle(VideoId.of(VIDEO), OwnerId.of(BOB), new VideoActor(OwnerId.of(ALICE), true));
	}

	@Test
	void everyErrorHasItsOwnStatusAndStableCode() throws Exception {
		var manager = token(ALICE, "videos:read", "videos:write", "videos:manage");

		given(getVideo.handle(any())).willThrow(new VideoNotFoundException("No video with ID " + VIDEO));
		mvc.perform(get("/api/v1/videos/" + VIDEO).with(manager)).andExpect(status().isNotFound())
				.andExpect(problem("video-not-found")).andExpect(jsonPath("$.detail").value("No video with ID " + VIDEO));

		given(createVideo.handle(any(), any())).willThrow(new DuplicateVideoException("Another video is already titled 'Intro'"));
		mvc.perform(post("/api/v1/videos").with(manager).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isConflict()).andExpect(problem("duplicate-video"));

		given(updateVideo.handle(any(), any(), any()))
				.willThrow(new OperationNotPermittedException("A video may only be changed by its owner or a video manager"));
		mvc.perform(put("/api/v1/videos/" + VIDEO).with(manager).contentType(MediaType.APPLICATION_JSON).content(VALID_VIDEO))
				.andExpect(status().isForbidden()).andExpect(problem("operation-not-permitted"));

		given(transferVideo.handle(any(), any(), any())).willThrow(new OwnerNotEligibleException("No user with ID " + BOB));
		mvc.perform(put("/api/v1/videos/" + VIDEO + "/owner").with(manager).contentType(MediaType.APPLICATION_JSON)
				.content(TRANSFER)).andExpect(status().isUnprocessableContent()).andExpect(problem("owner-not-eligible"));
	}

	@Test
	void platformFailuresAndUnexpectedErrorsRevealNothingInternal() throws Exception {
		var manager = token(ALICE, "videos:read", "videos:manage");
		given(transferVideo.handle(any(), any(), any())).willThrow(new UserDirectoryUnavailableException(
				"The user directory could not be asked about a user", new IllegalStateException("secret-internal-detail")));
		given(getVideo.handle(any())).willThrow(new IllegalStateException("jdbc:postgresql://secret-internal-detail"));

		mvc.perform(put("/api/v1/videos/" + VIDEO + "/owner").with(manager).contentType(MediaType.APPLICATION_JSON)
				.content(TRANSFER))
				.andExpect(status().isServiceUnavailable()).andExpect(problem("user-directory-unavailable"))
				.andExpect(content().string(not(containsString("secret-internal"))));
		mvc.perform(get("/api/v1/videos/" + VIDEO).with(manager))
				.andExpect(status().isInternalServerError()).andExpect(problem("internal-error"))
				.andExpect(content().string(not(containsString("secret-internal"))));
	}

	@Test
	void searchingBuildsTheFiltersAndReturnsAPageWithTotal() throws Exception {
		given(searchVideos.handle(any())).willReturn(new PageResult<>(List.of(video(ALICE, true)), 41, 2, 5));

		mvc.perform(get("/api/v1/videos?title=intro&completed=true&ownerId=" + BOB + "&page=2&size=5&sort=createdAt,desc")
				.with(token(ALICE, "videos:read")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(41)).andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.size").value(5)).andExpect(jsonPath("$.items[0].title").value("Intro"));

		verify(searchVideos).handle(new VideoSearch("intro", true, OwnerId.of(BOB), new Paging(2, 5, "createdAt", false)));
	}

	@Test
	void ownerMeLimitsTheSearchToTheCallersOwnVideos() throws Exception {
		given(searchVideos.handle(any())).willReturn(new PageResult<>(List.of(), 0, 0, 20));

		mvc.perform(get("/api/v1/videos?owner=me").with(token(ALICE, "videos:read"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(searchVideos).handle(new VideoSearch(null, null, OwnerId.of(ALICE), new Paging(0, 20, "title", true)));
	}

	@Test
	void badSearchParametersAreRejected() throws Exception {
		var reader = token(ALICE, "videos:read");

		for (String query : List.of("sort=completed", "sort=title,sideways", "size=0", "size=101", "page=-1", "owner=bob",
				"ownerId=nope", "completed=maybe", "page=x")) {
			mvc.perform(get("/api/v1/videos?" + query).with(reader)).andExpect(status().isBadRequest())
					.andExpect(problem("invalid-value"));
		}
		verifyNoInteractions(searchVideos);
	}

	// --- helpers

	private static JwtRequestPostProcessor token(String subject, String... permissions) {
		return jwt().jwt(token -> token.subject(subject)
				.claim("realm_access", Map.of("roles", List.of("USER")))
				.claim("permissions", List.of(permissions)))
				.authorities(new PlatformAuthoritiesConverter());
	}

	private static ResultMatcher problem(String code) {
		return result -> {
			content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.code").value(code).match(result);
			jsonPath("$.type").value(ProblemResponses.TYPE_BASE + code).match(result);
		};
	}

	private static Video video(String owner, boolean completed) {
		return Video.rehydrate(VideoId.of(VIDEO), new VideoDetails(new Title("Intro"), "A first look.", completed),
				new OwnerId(UUID.fromString(owner)), CREATED, CREATED);
	}
}
