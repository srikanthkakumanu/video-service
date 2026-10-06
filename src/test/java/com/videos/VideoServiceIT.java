package com.videos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.videos.support.StubPlatform;
import com.videos.support.TestIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole service over HTTP with real Postgres, the real seed file, real signature validation
 * against a JWKS endpoint, and stand-ins for auth-service and user-service.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "videos.seed.enabled=true",
		"platform.directory.load-balanced=false" })
class VideoServiceIT {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};
	private static final int SEEDED = 20;
	private static final List<String> READER = List.of("videos:read");
	private static final List<String> EDITOR = List.of("videos:read", "videos:write");
	private static final List<String> MANAGER = List.of("videos:read", "videos:write", "videos:manage");

	static final TestIssuer issuer = new TestIssuer();
	static final StubPlatform platform = new StubPlatform();

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationRunner videoSeeder;

	private final String alice = UUID.randomUUID().toString();
	private final String bob = UUID.randomUUID().toString();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("platform.security.jwt.issuer-uri", () -> TestIssuer.ISSUER);
		registry.add("platform.security.jwt.jwk-set-uri", issuer::jwkSetUri);
		registry.add("platform.directory.user-service-url", platform::url);
		registry.add("platform.directory.auth-service-url", platform::url);
		registry.add("platform.directory.client-secret", () -> StubPlatform.CLIENT_SECRET);
	}

	@BeforeEach
	void knownUsers() {
		platform.reset();
		platform.user(alice, "ACTIVE");
		platform.user(bob, "ACTIVE");
	}

	@Test
	void theSeedFileIsLoadedCompletelyAndLoadingAgainChangesNothing() throws Exception {
		assertThat(count("select count(*) from video where owner_id is null")).isEqualTo(SEEDED);
		assertThat(count("select count(distinct title) from video where owner_id is null")).isEqualTo(SEEDED);
		assertThat(count("select count(*) from video where owner_id is null and description is null")).isZero();
		long version = count("select coalesce(sum(version), 0) from video where owner_id is null");

		videoSeeder.run(new DefaultApplicationArguments());

		assertThat(count("select count(*) from video where owner_id is null")).isEqualTo(SEEDED);
		assertThat(count("select coalesce(sum(version), 0) from video where owner_id is null")).isEqualTo(version);
	}

	@Test
	void nothingIsUsableWithoutLoggingInAndAVideoRoleIsNeededOnTop() {
		assertProblem(call(HttpMethod.GET, "/api/v1/videos", null, null), 401, "unauthorized");
		assertProblem(call(HttpMethod.GET, "/api/v1/videos", "not.a.token", null), 401, "unauthorized");
		assertProblem(call(HttpMethod.GET, "/api/v1/videos", issuer.token(claims -> claims.audience("books-service")
				.claim("permissions", MANAGER)), null), 401, "unauthorized");

		// Logged in, but with only the default USER role, or only book permissions.
		assertProblem(call(HttpMethod.GET, "/api/v1/videos", issuer.token(alice, List.of("USER"), List.of()), null), 403,
				"forbidden");
		assertProblem(call(HttpMethod.GET, "/api/v1/videos", issuer.token(alice, List.of("USER", "CATALOG_MANAGER"),
				List.of("books:read", "books:write", "books:manage")), null), 403, "forbidden");

		assertThat(call(HttpMethod.GET, "/actuator/health/readiness", null, null).getStatusCode().value()).isEqualTo(200);
		assertThat(call(HttpMethod.GET, "/v3/api-docs", null, null).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void aReaderBrowsesTheStarterSet() {
		String reader = issuer.token(alice, List.of("USER", "VIDEO_READER"), READER);

		var firstPage = call(HttpMethod.GET, "/api/v1/videos?size=5&sort=title,asc", reader, null);
		assertThat(firstPage.getStatusCode().value()).isEqualTo(200);
		assertThat(((Number) firstPage.getBody().get("total")).intValue()).isGreaterThanOrEqualTo(SEEDED);
		assertThat(items(firstPage)).hasSize(5);

		var found = call(HttpMethod.GET, "/api/v1/videos?title=spring%20boot", reader, null);
		assertThat(items(found)).singleElement().satisfies(video -> assertThat(video)
				.containsEntry("title", "Spring Boot in One Hour").containsEntry("ownerId", null)
				.containsEntry("completed", false).containsKey("description"));

		String seededId = (String) items(found).getFirst().get("id");
		assertProblem(call(HttpMethod.POST, "/api/v1/videos", reader, Map.of("title", title())), 403, "forbidden");
		assertProblem(call(HttpMethod.DELETE, "/api/v1/videos/" + seededId, reader, null), 403, "forbidden");
	}

	@Test
	void anEditorOwnsWhatTheyAddAndNobodyElseMayChangeIt() {
		String aliceToken = issuer.token(alice, List.of("USER", "VIDEO_EDITOR"), EDITOR);
		String bobToken = issuer.token(bob, List.of("USER", "VIDEO_EDITOR"), EDITOR);
		String title = title();
		int lookupsBefore = platform.authorizationHeaders().size();

		var created = call(HttpMethod.POST, "/api/v1/videos", aliceToken, Map.of("title", title, "description", "Mine."));
		String id = (String) created.getBody().get("id");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		assertThat(created.getHeaders().getLocation()).hasToString("/api/v1/videos/" + id);
		assertThat(created.getBody()).containsEntry("ownerId", alice).containsEntry("completed", false);
		assertThat(jdbc.queryForObject("select owner_id::text from video where id = ?::uuid", String.class, id))
				.isEqualTo(alice);

		assertProblem(call(HttpMethod.POST, "/api/v1/videos", bobToken, Map.of("title", title)), 409, "duplicate-video");
		assertProblem(call(HttpMethod.PUT, "/api/v1/videos/" + id, bobToken, Map.of("title", title())), 403,
				"operation-not-permitted");
		assertProblem(call(HttpMethod.POST, "/api/v1/videos/" + id + "/complete", bobToken, null), 403,
				"operation-not-permitted");
		assertProblem(call(HttpMethod.DELETE, "/api/v1/videos/" + id, bobToken, null), 403, "operation-not-permitted");
		// An editor cannot add a video in someone else's name, nor touch the starter set.
		assertProblem(call(HttpMethod.POST, "/api/v1/videos", aliceToken, Map.of("title", title(), "ownerId", bob)), 403,
				"operation-not-permitted");
		String seededId = (String) items(call(HttpMethod.GET, "/api/v1/videos?title=Graceful%20Shutdown", aliceToken,
				null)).getFirst().get("id");
		assertProblem(call(HttpMethod.DELETE, "/api/v1/videos/" + seededId, aliceToken, null), 403,
				"operation-not-permitted");

		String newTitle = title();
		assertThat(call(HttpMethod.PUT, "/api/v1/videos/" + id, aliceToken, Map.of("title", newTitle)).getBody())
				.containsEntry("title", newTitle).containsEntry("description", null).containsEntry("ownerId", alice);
		assertThat(call(HttpMethod.POST, "/api/v1/videos/" + id + "/complete", aliceToken, null).getBody())
				.containsEntry("completed", true);

		assertThat(items(call(HttpMethod.GET, "/api/v1/videos?owner=me", aliceToken, null)))
				.extracting(video -> video.get("id")).containsExactly(id);
		assertThat(items(call(HttpMethod.GET, "/api/v1/videos?owner=me&completed=false", aliceToken, null))).isEmpty();
		assertThat(items(call(HttpMethod.GET, "/api/v1/videos?owner=me", bobToken, null))).isEmpty();

		assertThat(call(HttpMethod.DELETE, "/api/v1/videos/" + id, aliceToken, null).getStatusCode().value()).isEqualTo(204);
		assertProblem(call(HttpMethod.GET, "/api/v1/videos/" + id, aliceToken, null), 404, "video-not-found");
		assertThat(platform.authorizationHeaders()).as("an editor's own videos need no user lookup").hasSize(lookupsBefore);
	}

	@Test
	void aManagerTransfersVideosOnlyToActivePlatformUsersAndChangesTheStarterSet() {
		String manager = issuer.token(UUID.randomUUID().toString(), List.of("USER", "VIDEO_MANAGER"), MANAGER);
		String aliceToken = issuer.token(alice, List.of("USER", "VIDEO_EDITOR"), EDITOR);
		String bobToken = issuer.token(bob, List.of("USER", "VIDEO_EDITOR"), EDITOR);
		String disabled = UUID.randomUUID().toString();
		platform.user(disabled, "DISABLED");
		String id = (String) call(HttpMethod.POST, "/api/v1/videos", aliceToken, Map.of("title", title())).getBody()
				.get("id");

		assertProblem(call(HttpMethod.PUT, "/api/v1/videos/" + id + "/owner", aliceToken, Map.of("ownerId", bob)), 403,
				"forbidden");
		assertProblem(call(HttpMethod.PUT, "/api/v1/videos/" + id + "/owner", manager,
				Map.of("ownerId", UUID.randomUUID().toString())), 422, "owner-not-eligible");
		assertProblem(call(HttpMethod.PUT, "/api/v1/videos/" + id + "/owner", manager, Map.of("ownerId", disabled)), 422,
				"owner-not-eligible");
		platform.failUserLookupsWith(503);
		assertProblem(call(HttpMethod.PUT, "/api/v1/videos/" + id + "/owner", manager, Map.of("ownerId", bob)), 503,
				"user-directory-unavailable");
		platform.failUserLookupsWith(0);
		assertThat(call(HttpMethod.GET, "/api/v1/videos/" + id, manager, null).getBody()).containsEntry("ownerId", alice);

		assertThat(call(HttpMethod.PUT, "/api/v1/videos/" + id + "/owner", manager, Map.of("ownerId", bob)).getBody())
				.containsEntry("ownerId", bob);
		// The lookup was made as this service, with its own token, not with the caller's.
		assertThat(platform.authorizationHeaders()).isNotEmpty().allMatch(header -> header.startsWith("Bearer service-token-"));
		assertProblem(call(HttpMethod.DELETE, "/api/v1/videos/" + id, aliceToken, null), 403, "operation-not-permitted");
		assertThat(call(HttpMethod.DELETE, "/api/v1/videos/" + id, bobToken, null).getStatusCode().value()).isEqualTo(204);

		// A manager adds a video directly for another user, and may change the starter set.
		var gift = call(HttpMethod.POST, "/api/v1/videos", manager, Map.of("title", title(), "ownerId", bob));
		assertThat(gift.getStatusCode().value()).isEqualTo(201);
		assertThat(gift.getBody()).containsEntry("ownerId", bob);
		var seeded = items(call(HttpMethod.GET, "/api/v1/videos?title=Reading%20a%20Stack%20Trace", manager, null)).getFirst();
		assertThat(call(HttpMethod.PUT, "/api/v1/videos/" + seeded.get("id"), manager,
				Map.of("title", seeded.get("title"), "description", "Reviewed.")).getBody())
				.containsEntry("description", "Reviewed.").containsEntry("ownerId", null);
		assertThat(call(HttpMethod.DELETE, "/api/v1/videos/" + gift.getBody().get("id"), manager, null).getStatusCode()
				.value()).isEqualTo(204);
	}

	// --- helpers

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	/** A fresh title within the 30-character limit. */
	private static String title() {
		return "Test " + UUID.randomUUID().toString().substring(0, 13);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(ResponseEntity<Map<String, Object>> response) {
		return (List<Map<String, Object>>) response.getBody().get("items");
	}

	private static void assertProblem(ResponseEntity<Map<String, Object>> response, int status, String code) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(response.getBody()).containsEntry("code", code)
				.containsEntry("type", "https://platform.local/problems/" + code);
	}

	private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, String token, Object body) {
		var request = RestClient.create().method(method).uri(java.net.URI.create("http://localhost:" + port + path));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return request.exchange((req, res) -> {
			Map<String, Object> parsed = res.getHeaders().getContentLength() == 0 || res.getStatusCode().value() == 204
					? null : res.bodyTo(JSON);
			return ResponseEntity.status(res.getStatusCode()).headers(res.getHeaders()).body(parsed);
		});
	}
}
