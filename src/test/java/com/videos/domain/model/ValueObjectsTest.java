package com.videos.domain.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.videos.domain.exception.InvalidValueException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ValueObjectsTest {

	@Test
	void identifiersParseUuidsAndRejectAnythingElse() {
		String id = "7c1f0e9a-3b5d-4f6a-8b7c-9d0e1f2a3b4c";

		assertThat(VideoId.of(id).value()).isEqualTo(UUID.fromString(id));
		assertThat(VideoId.of(" " + id + " ")).hasToString(id);
		assertThat(OwnerId.of(id)).isEqualTo(new OwnerId(UUID.fromString(id))).hasToString(id);
		assertThat(VideoId.newId()).isNotEqualTo(VideoId.newId());

		assertInvalid("id", () -> VideoId.of("not-a-uuid"));
		assertInvalid("id", () -> VideoId.of(" "));
		assertInvalid("id", () -> new VideoId(null));
		assertInvalid("ownerId", () -> OwnerId.of(null));
	}

	@Test
	void aTitleIsTrimmedRequiredAndAtMostThirtyCharacters() {
		assertThat(new Title("  Intro ").value()).isEqualTo("Intro");
		assertThat(new Title("x".repeat(Title.MAX_LENGTH))).hasToString("x".repeat(30));

		assertInvalid("title", () -> new Title(" "));
		assertInvalid("title", () -> new Title(null));
		assertInvalid("title", () -> new Title("x".repeat(Title.MAX_LENGTH + 1)));
	}

	@Test
	void videoDetailsNeedATitleAndBoundTheDescription() {
		var title = new Title("Intro");

		assertThat(new VideoDetails(title, "  ", false).description()).isNull();
		assertThat(new VideoDetails(title, " A first look. ", true).description()).isEqualTo("A first look.");
		assertThat(new VideoDetails(title, null, false).asCompleted()).isEqualTo(new VideoDetails(title, null, true));

		assertInvalid("title", () -> new VideoDetails(null, null, false));
		assertInvalid("description",
				() -> new VideoDetails(title, "x".repeat(VideoDetails.MAX_DESCRIPTION_LENGTH + 1), false));
	}

	@Test
	void pagingAcceptsOnlyKnownSortFieldsAndSaneSizes() {
		Set<String> fields = VideoSearch.SORT_FIELDS;

		assertThat(Paging.of(0, 20, null, "title", fields)).isEqualTo(new Paging(0, 20, "title", true));
		assertThat(Paging.of(2, 5, "createdAt,desc", "title", fields)).isEqualTo(new Paging(2, 5, "createdAt", false));
		assertThat(Paging.of(0, 1, " title , ASC ", "title", fields).ascending()).isTrue();

		assertInvalid("sort", () -> Paging.of(0, 20, "completed", "title", fields));
		assertInvalid("sort", () -> Paging.of(0, 20, "title,sideways", "title", fields));
		assertInvalid("sort", () -> Paging.of(0, 20, "title,asc,extra", "title", fields));
		assertInvalid("page", () -> Paging.of(-1, 20, null, "title", fields));
		assertInvalid("size", () -> Paging.of(0, 0, null, "title", fields));
		assertInvalid("size", () -> Paging.of(0, Paging.MAX_SIZE + 1, null, "title", fields));
	}

	@Test
	void aSearchIgnoresABlankTitleFilter() {
		var paging = new Paging(0, 20, "title", true);

		assertThat(new VideoSearch(" java ", null, null, paging).title()).isEqualTo("java");
		assertThat(new VideoSearch("  ", true, null, paging).title()).isNull();
	}

	@Test
	void aPageOfResultsCanBeMappedAndIsImmutable() {
		var page = new PageResult<>(new java.util.ArrayList<>(List.of(1, 2)), 7, 0, 2);

		assertThat(page.map(String::valueOf)).isEqualTo(new PageResult<>(List.of("1", "2"), 7, 0, 2));
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> page.items().add(3));
	}

	@Test
	void anActorNeedsAUser() {
		var user = new OwnerId(UUID.randomUUID());

		assertThat(new VideoActor(user, false).is(user)).isTrue();
		assertThat(new VideoActor(user, false).is(new OwnerId(UUID.randomUUID()))).isFalse();
		assertInvalid("userId", () -> new VideoActor(null, true));
	}

	static void assertInvalid(String field, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatExceptionOfType(InvalidValueException.class).isThrownBy(call)
				.satisfies(ex -> assertThat(ex.field()).isEqualTo(field))
				.satisfies(ex -> assertThat(ex.code()).isEqualTo("invalid-value"));
	}
}
