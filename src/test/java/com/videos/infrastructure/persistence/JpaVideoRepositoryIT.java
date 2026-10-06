package com.videos.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.videos.domain.exception.DuplicateVideoException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.Paging;
import com.videos.domain.model.Title;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoActor;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** The video repository against real Postgres, with the schema created by Flyway and validated by Hibernate. */
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaVideoRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaVideoRepositoryIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	@Autowired
	private JpaVideoRepository videos;

	@Autowired
	private JdbcTemplate jdbc;

	private final OwnerId alice = new OwnerId(UUID.randomUUID());
	private final OwnerId bob = new OwnerId(UUID.randomUUID());

	@BeforeEach
	void empty() {
		jdbc.update("delete from video");
	}

	@Test
	void flywayCreatedTheSchema() {
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name = 'video'",
				String.class)).contains("owner_id", "title", "completed").doesNotContain("user_name", "user_id");
	}

	@Test
	void aVideoIsStoredAndReadBackWithItsOwner() {
		Video video = videos.save(video("Intro", alice));

		Video found = videos.findById(video.id()).orElseThrow();

		assertThat(found.details()).isEqualTo(video.details());
		assertThat(found.ownerId()).contains(alice);
		assertThat(found.createdAt()).isEqualTo(CREATED);
		assertThat(videos.exists(video.id())).isTrue();
		assertThat(videos.findByTitle(new Title("Intro"))).map(Video::id).contains(video.id());
		assertThat(videos.findByTitle(new Title("Outro"))).isEmpty();
		assertThat(videos.findById(VideoId.newId())).isEmpty();
	}

	@Test
	void anUnownedVideoHasANullOwnerColumn() {
		Video video = videos.save(Video.unowned(VideoId.newId(), details("Starter"), CREATED));

		assertThat(videos.findById(video.id()).orElseThrow().ownerId()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from video where owner_id is null", Integer.class)).isEqualTo(1);
	}

	@Test
	void savingAgainUpdatesTheRowAndKeepsTheCreationTime() {
		Video video = videos.save(video("Intro", alice));

		videos.save(video.complete(new VideoActor(alice, false), LATER));

		Video found = videos.findById(video.id()).orElseThrow();
		assertThat(found.details().completed()).isTrue();
		assertThat(found.createdAt()).isEqualTo(CREATED);
		assertThat(found.updatedAt()).isEqualTo(LATER);
		assertThat(jdbc.queryForObject("select count(*) from video", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select version from video", Long.class)).isEqualTo(1);
	}

	@Test
	void theDatabaseRefusesASecondVideoWithTheSameTitle() {
		videos.save(video("Intro", alice));

		assertThatExceptionOfType(DuplicateVideoException.class).isThrownBy(() -> videos.save(video("Intro", bob)));
		assertThat(jdbc.queryForObject("select count(*) from video", Integer.class)).isEqualTo(1);
	}

	@Test
	void videosAreFilteredSortedAndPaged() {
		videos.save(video("Java Basics", alice));
		Video advanced = videos.save(video("Advanced Java", bob));
		videos.save(advanced.complete(new VideoActor(bob, false), LATER));
		videos.save(video("Kotlin", alice));
		videos.save(Video.unowned(VideoId.newId(), details("100% Go_Lang"), CREATED));

		assertThat(titles(new VideoSearch(null, null, null, paging(0, 10, "title", true))))
				.containsExactly("100% Go_Lang", "Advanced Java", "Java Basics", "Kotlin");
		assertThat(titles(new VideoSearch("jAVa", null, null, paging(0, 10, "title", false))))
				.containsExactly("Java Basics", "Advanced Java");
		assertThat(titles(new VideoSearch(null, true, null, paging(0, 10, "title", true)))).containsExactly("Advanced Java");
		assertThat(titles(new VideoSearch(null, false, alice, paging(0, 10, "title", true))))
				.containsExactly("Java Basics", "Kotlin");
		// Wildcard characters in the caller's text are taken literally.
		assertThat(titles(new VideoSearch("%", null, null, paging(0, 10, "title", true)))).containsExactly("100% Go_Lang");
		assertThat(titles(new VideoSearch("o_l", null, null, paging(0, 10, "title", true)))).containsExactly("100% Go_Lang");

		var secondPage = videos.search(new VideoSearch(null, null, null, paging(1, 3, "title", true)));
		assertThat(secondPage.total()).isEqualTo(4);
		assertThat(secondPage.page()).isEqualTo(1);
		assertThat(secondPage.items()).extracting(video -> video.details().title().value()).containsExactly("Kotlin");
	}

	@Test
	void aDeletedVideoIsGone() {
		Video video = videos.save(video("Intro", alice));

		videos.delete(video.id());

		assertThat(videos.exists(video.id())).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from video", Integer.class)).isZero();
	}

	private List<String> titles(VideoSearch search) {
		return videos.search(search).items().stream().map(video -> video.details().title().value()).toList();
	}

	private static Paging paging(int page, int size, String sortBy, boolean ascending) {
		return new Paging(page, size, sortBy, ascending);
	}

	private static Video video(String title, OwnerId owner) {
		return Video.create(VideoId.newId(), details(title), null, new VideoActor(owner, false), CREATED);
	}

	private static VideoDetails details(String title) {
		return new VideoDetails(new Title(title), null, false);
	}
}
