package com.videos.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.videos.application.SeedVideos.SeedVideo;
import com.videos.domain.exception.DuplicateVideoException;
import com.videos.domain.exception.InvalidValueException;
import com.videos.domain.exception.OperationNotPermittedException;
import com.videos.domain.exception.OwnerNotEligibleException;
import com.videos.domain.exception.VideoNotFoundException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Paging;
import com.videos.domain.model.Title;
import com.videos.domain.model.UserStanding;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** The video use cases against in-memory ports. */
class VideoUseCasesTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
	private static final Paging FIRST_PAGE = new Paging(0, 20, "title", true);

	private final InMemoryVideos videos = new InMemoryVideos();
	private final InMemoryVideos.Directory directory = new InMemoryVideos.Directory();
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final CreateVideo createVideo = new CreateVideo(videos, directory, clock);
	private final UpdateVideo updateVideo = new UpdateVideo(videos, clock);
	private final CompleteVideo completeVideo = new CompleteVideo(videos, clock);
	private final TransferVideo transferVideo = new TransferVideo(videos, directory, clock);
	private final DeleteVideo deleteVideo = new DeleteVideo(videos);
	private final GetVideo getVideo = new GetVideo(videos);
	private final SearchVideos searchVideos = new SearchVideos(videos);
	private final SeedVideos seedVideos = new SeedVideos(videos, clock);

	private final OwnerId alice = new OwnerId(UUID.randomUUID());
	private final OwnerId bob = new OwnerId(UUID.randomUUID());
	private final VideoActor aliceActor = new VideoActor(alice, false);
	private final VideoActor bobActor = new VideoActor(bob, false);
	private final VideoActor manager = new VideoActor(new OwnerId(UUID.randomUUID()), true);

	@BeforeEach
	void knownUsers() {
		directory.users.put(alice, UserStanding.ACTIVE);
		directory.users.put(bob, UserStanding.ACTIVE);
	}

	@Test
	void addingAVideoForYourselfStoresItAndAsksNobodyAboutUsers() {
		Video video = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor);

		assertThat(video.ownerId()).contains(alice);
		assertThat(video.createdAt()).isEqualTo(NOW);
		assertThat(videos.stored).containsKey(video.id());
		assertThat(directory.lookups).isZero();
	}

	@Test
	void aManagerAddsAVideoForAnActiveUserAfterAskingThePlatform() {
		Video video = createVideo.handle(new CreateVideo.Command(details("Intro"), bob), manager);

		assertThat(video.ownerId()).contains(bob);
		assertThat(directory.lookups).isEqualTo(1);
	}

	@Test
	void aVideoIsNotAddedForAnUnknownOrInactiveUser() {
		OwnerId stranger = new OwnerId(UUID.randomUUID());
		directory.users.put(bob, UserStanding.INACTIVE);

		assertThatExceptionOfType(OwnerNotEligibleException.class)
				.isThrownBy(() -> createVideo.handle(new CreateVideo.Command(details("Intro"), stranger), manager))
				.withMessageContaining("No user").satisfies(ex -> assertThat(ex.code()).isEqualTo("owner-not-eligible"));
		assertThatExceptionOfType(OwnerNotEligibleException.class)
				.isThrownBy(() -> createVideo.handle(new CreateVideo.Command(details("Intro"), bob), manager))
				.withMessageContaining("not active");
		assertThat(videos.stored).isEmpty();
	}

	@Test
	void anOrdinaryUserCannotAddAVideoForSomeoneElse() {
		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> createVideo.handle(new CreateVideo.Command(details("Intro"), bob), aliceActor));
		assertThat(videos.stored).isEmpty();
		assertThat(directory.lookups).isZero();
	}

	@Test
	void noTwoVideosShareATitle() {
		VideoId intro = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor).id();
		createVideo.handle(new CreateVideo.Command(details("Outro"), null), aliceActor);

		assertThatExceptionOfType(DuplicateVideoException.class)
				.isThrownBy(() -> createVideo.handle(new CreateVideo.Command(details("Intro"), null), bobActor))
				.satisfies(ex -> assertThat(ex.code()).isEqualTo("duplicate-video"));
		assertThatExceptionOfType(DuplicateVideoException.class)
				.isThrownBy(() -> updateVideo.handle(intro, details("Outro"), aliceActor));
		assertThat(videos.stored).hasSize(2);
		// Keeping its own title is not a duplicate.
		assertThat(updateVideo.handle(intro, new VideoDetails(new Title("Intro"), "Now described.", false), aliceActor)
				.details().description()).isEqualTo("Now described.");
	}

	@Test
	void updatingIsRefusedForOthersAndForAMissingVideo() {
		VideoId id = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor).id();

		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> updateVideo.handle(id, details("Hijacked"), bobActor));
		assertThatExceptionOfType(VideoNotFoundException.class)
				.isThrownBy(() -> updateVideo.handle(VideoId.newId(), details("Intro"), aliceActor))
				.satisfies(ex -> assertThat(ex.code()).isEqualTo("video-not-found"));
		assertThat(updateVideo.handle(id, details("Corrected"), manager).details().title().value()).isEqualTo("Corrected");
		assertThat(getVideo.handle(id).details().title().value()).isEqualTo("Corrected");
	}

	@Test
	void theOwnerCompletesTheirVideoOnceAndOthersCannot() {
		VideoId id = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor).id();
		int savesBefore = videos.saves;

		assertThatExceptionOfType(OperationNotPermittedException.class).isThrownBy(() -> completeVideo.handle(id, bobActor));
		assertThat(completeVideo.handle(id, aliceActor).details().completed()).isTrue();
		assertThat(completeVideo.handle(id, aliceActor).details().completed()).isTrue();

		assertThat(videos.saves).as("completing twice writes once").isEqualTo(savesBefore + 1);
		assertThat(getVideo.handle(id).details().completed()).isTrue();
		assertThatExceptionOfType(VideoNotFoundException.class)
				.isThrownBy(() -> completeVideo.handle(VideoId.newId(), aliceActor));
	}

	@Test
	void onlyTheOwnerOrAManagerDeletesAVideo() {
		VideoId first = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor).id();
		VideoId second = createVideo.handle(new CreateVideo.Command(details("Outro"), null), aliceActor).id();

		assertThatExceptionOfType(OperationNotPermittedException.class).isThrownBy(() -> deleteVideo.handle(first, bobActor));
		assertThat(videos.stored).hasSize(2);

		deleteVideo.handle(first, aliceActor);
		deleteVideo.handle(second, manager);

		assertThat(videos.stored).isEmpty();
		assertThatExceptionOfType(VideoNotFoundException.class).isThrownBy(() -> deleteVideo.handle(first, aliceActor));
		assertThatExceptionOfType(VideoNotFoundException.class).isThrownBy(() -> getVideo.handle(first));
	}

	@Test
	void aManagerTransfersAVideoToAnActiveUserOnly() {
		VideoId id = createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor).id();
		OwnerId stranger = new OwnerId(UUID.randomUUID());

		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> transferVideo.handle(id, bob, aliceActor));
		assertThatExceptionOfType(OwnerNotEligibleException.class)
				.isThrownBy(() -> transferVideo.handle(id, stranger, manager));
		assertThat(getVideo.handle(id).ownerId()).contains(alice);

		assertThat(transferVideo.handle(id, bob, manager).ownerId()).contains(bob);
		assertThatExceptionOfType(VideoNotFoundException.class)
				.isThrownBy(() -> transferVideo.handle(VideoId.newId(), bob, manager));
	}

	@Test
	void searchingCanBeLimitedToAnOwnerAndToCompletedVideos() {
		createVideo.handle(new CreateVideo.Command(details("Intro"), null), aliceActor);
		VideoId outro = createVideo.handle(new CreateVideo.Command(details("Outro"), null), bobActor).id();
		completeVideo.handle(outro, bobActor);

		assertThat(searchVideos.handle(new VideoSearch(null, null, null, FIRST_PAGE)).total()).isEqualTo(2);
		assertThat(searchVideos.handle(new VideoSearch(null, null, bob, FIRST_PAGE)).items())
				.extracting(video -> video.details().title().value()).containsExactly("Outro");
		assertThat(searchVideos.handle(new VideoSearch(null, false, null, FIRST_PAGE)).items())
				.extracting(video -> video.details().title().value()).containsExactly("Intro");
	}

	@Test
	void seedingAddsUnownedVideosAndIsSafeToRepeat() {
		var seed = List.of(new SeedVideo(UUID.randomUUID().toString(), "Starter One", "The first."),
				new SeedVideo(UUID.randomUUID().toString(), "Starter Two", null));

		assertThat(seedVideos.handle(seed)).isEqualTo(2);

		Video one = videos.findByTitle(new Title("Starter One")).orElseThrow();
		assertThat(one.ownerId()).isEmpty();
		assertThat(one.details().description()).isEqualTo("The first.");
		assertThat(one.details().completed()).isFalse();
		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> updateVideo.handle(one.id(), details("Mine now"), aliceActor));

		// A later edit survives a second load, and nothing is added twice.
		updateVideo.handle(one.id(), details("Starter One (edited)"), manager);
		assertThat(seedVideos.handle(seed)).isZero();
		assertThat(videos.stored).hasSize(2);
		assertThat(videos.findById(one.id()).orElseThrow().details().title().value()).isEqualTo("Starter One (edited)");
	}

	@Test
	void seedDataThatBreaksADomainRuleIsRejected() {
		assertThatExceptionOfType(InvalidValueException.class).isThrownBy(() -> seedVideos.handle(
				List.of(new SeedVideo(UUID.randomUUID().toString(), "x".repeat(31), null))))
				.satisfies(ex -> assertThat(ex.field()).isEqualTo("title"));
		assertThatExceptionOfType(InvalidValueException.class)
				.isThrownBy(() -> seedVideos.handle(List.of(new SeedVideo("not-a-uuid", "Fine", null))));
		assertThatExceptionOfType(DuplicateVideoException.class).isThrownBy(() -> seedVideos.handle(
				List.of(new SeedVideo(UUID.randomUUID().toString(), "Same", null),
						new SeedVideo(UUID.randomUUID().toString(), "Same", null))));
	}

	private static VideoDetails details(String title) {
		return new VideoDetails(new Title(title), null, false);
	}
}
