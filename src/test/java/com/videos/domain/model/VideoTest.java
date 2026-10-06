package com.videos.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.videos.domain.exception.OperationNotPermittedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Who may add, change, complete and hand over a video. */
class VideoTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	private final OwnerId alice = new OwnerId(UUID.randomUUID());
	private final OwnerId bob = new OwnerId(UUID.randomUUID());
	private final VideoActor aliceActor = new VideoActor(alice, false);
	private final VideoActor bobActor = new VideoActor(bob, false);
	private final VideoActor manager = new VideoActor(new OwnerId(UUID.randomUUID()), true);
	private final VideoDetails details = details("Intro");

	@Test
	void aVideoBelongsToWhoeverAddsIt() {
		Video video = Video.create(VideoId.newId(), details, null, aliceActor, CREATED);

		assertThat(video.ownerId()).contains(alice);
		assertThat(video.isOwnedBy(alice)).isTrue();
		assertThat(video.isOwnedBy(bob)).isFalse();
		assertThat(video.details()).isEqualTo(details);
		assertThat(video.createdAt()).isEqualTo(CREATED);
		assertThat(video.updatedAt()).isEqualTo(CREATED);
		assertThat(Video.create(VideoId.newId(), details, alice, aliceActor, CREATED).ownerId()).contains(alice);
	}

	@Test
	void onlyAManagerMayAddAVideoForSomeoneElse() {
		assertThat(Video.create(VideoId.newId(), details, bob, manager, CREATED).ownerId()).contains(bob);

		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> Video.create(VideoId.newId(), details, bob, aliceActor, CREATED))
				.satisfies(ex -> assertThat(ex.code()).isEqualTo("operation-not-permitted"));
	}

	@Test
	void theOwnerChangesTheirVideoAndTheCreationTimeStays() {
		Video video = Video.create(VideoId.newId(), details, null, aliceActor, CREATED);

		Video revised = video.revise(details("Intro, part 2"), aliceActor, LATER);

		assertThat(revised.id()).isEqualTo(video.id());
		assertThat(revised.details().title().value()).isEqualTo("Intro, part 2");
		assertThat(revised.ownerId()).contains(alice);
		assertThat(revised.createdAt()).isEqualTo(CREATED);
		assertThat(revised.updatedAt()).isEqualTo(LATER);
		assertThat(video.details().title().value()).isEqualTo("Intro");
	}

	@Test
	void someoneElseCannotChangeAVideoButAManagerCan() {
		Video video = Video.create(VideoId.newId(), details, null, aliceActor, CREATED);

		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> video.revise(details("Hijacked"), bobActor, LATER));
		assertThatExceptionOfType(OperationNotPermittedException.class).isThrownBy(() -> video.requireWriteAccess(bobActor));
		assertThatNoException().isThrownBy(() -> video.requireWriteAccess(aliceActor));
		assertThat(video.revise(details("Corrected"), manager, LATER).details().title().value()).isEqualTo("Corrected");
	}

	@Test
	void completingIsAChangeOnlyTheOwnerOrAManagerMayMake() {
		Video video = Video.create(VideoId.newId(), details, null, aliceActor, CREATED);

		Video completed = video.complete(aliceActor, LATER);

		assertThat(completed.details().completed()).isTrue();
		assertThat(completed.details().title()).isEqualTo(details.title());
		assertThat(completed.updatedAt()).isEqualTo(LATER);
		assertThat(video.details().completed()).isFalse();
		assertThatExceptionOfType(OperationNotPermittedException.class).isThrownBy(() -> video.complete(bobActor, LATER));
		assertThat(video.complete(manager, LATER).details().completed()).isTrue();
	}

	@Test
	void completingACompletedVideoChangesNothing() {
		Video completed = Video.create(VideoId.newId(), details, null, aliceActor, CREATED).complete(aliceActor, LATER);

		assertThat(completed.complete(aliceActor, LATER.plusSeconds(60))).isSameAs(completed);
		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> completed.complete(bobActor, LATER));
	}

	@Test
	void anUnownedVideoIsChangedOnlyByAManager() {
		Video video = Video.unowned(VideoId.newId(), details, CREATED);

		assertThat(video.ownerId()).isEmpty();
		assertThat(video.isOwnedBy(alice)).isFalse();
		assertThatExceptionOfType(OperationNotPermittedException.class).isThrownBy(() -> video.requireWriteAccess(aliceActor));
		assertThatNoException().isThrownBy(() -> video.requireWriteAccess(manager));
	}

	@Test
	void onlyAManagerTransfersAVideoEvenTheOwnerCannot() {
		Video video = Video.create(VideoId.newId(), details, null, aliceActor, CREATED);

		Video transferred = video.transferTo(bob, manager, LATER);

		assertThat(transferred.ownerId()).contains(bob);
		assertThat(transferred.updatedAt()).isEqualTo(LATER);
		assertThat(transferred.details()).isEqualTo(details);
		assertThatExceptionOfType(OperationNotPermittedException.class)
				.isThrownBy(() -> video.transferTo(bob, aliceActor, LATER));
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> video.transferTo(null, manager, LATER));
	}

	@Test
	void rehydratingKeepsEverythingAsStored() {
		VideoId id = VideoId.newId();

		Video video = Video.rehydrate(id, details, bob, CREATED, LATER);

		assertThat(video.id()).isEqualTo(id);
		assertThat(video.ownerId()).contains(bob);
		assertThat(video.createdAt()).isEqualTo(CREATED);
		assertThat(video.updatedAt()).isEqualTo(LATER);
		assertThat(Video.rehydrate(id, details, null, CREATED, LATER).ownerId()).isEmpty();
	}

	private static VideoDetails details(String title) {
		return new VideoDetails(new Title(title), null, false);
	}
}
