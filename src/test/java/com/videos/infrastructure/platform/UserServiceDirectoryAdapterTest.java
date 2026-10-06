package com.videos.infrastructure.platform;

import java.time.Duration;
import java.util.UUID;

import com.videos.domain.exception.UserDirectoryUnavailableException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.UserStanding;
import com.videos.support.StubPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** The user lookup against a stand-in for auth-service and user-service, over real HTTP. */
class UserServiceDirectoryAdapterTest {

	private final StubPlatform platform = new StubPlatform();
	private final OwnerId user = new OwnerId(UUID.randomUUID());

	@AfterEach
	void stop() {
		platform.stop();
	}

	@Test
	void anActiveUserIsActive() {
		platform.user(user.toString(), "ACTIVE");

		assertThat(adapter(StubPlatform.CLIENT_SECRET).standingOf(user)).isEqualTo(UserStanding.ACTIVE);
		assertThat(platform.authorizationHeaders()).containsExactly("Bearer service-token-1");
	}

	@Test
	void aDisabledOrLockedUserIsInactiveAndAMissingOneUnknown() {
		OwnerId locked = new OwnerId(UUID.randomUUID());
		platform.user(user.toString(), "DISABLED");
		platform.user(locked.toString(), "LOCKED");
		var adapter = adapter(StubPlatform.CLIENT_SECRET);

		assertThat(adapter.standingOf(user)).isEqualTo(UserStanding.INACTIVE);
		assertThat(adapter.standingOf(locked)).isEqualTo(UserStanding.INACTIVE);
		assertThat(adapter.standingOf(new OwnerId(UUID.randomUUID()))).isEqualTo(UserStanding.UNKNOWN);
	}

	@Test
	void theServiceTokenIsReusedUntilItIsAboutToExpire() {
		platform.user(user.toString(), "ACTIVE");
		var adapter = adapter(StubPlatform.CLIENT_SECRET);

		adapter.standingOf(user);
		adapter.standingOf(user);
		assertThat(platform.tokensIssued()).isEqualTo(1);

		// A token that lives no longer than the safety margin is never reused.
		platform.tokenLifetimeSeconds(10);
		var shortLived = adapter(StubPlatform.CLIENT_SECRET);
		shortLived.standingOf(user);
		shortLived.standingOf(user);
		assertThat(platform.tokensIssued()).isEqualTo(3);
	}

	@Test
	void aRefusedTokenIsReplacedOnceAndTheLookupRepeated() {
		platform.user(user.toString(), "ACTIVE");
		var adapter = adapter(StubPlatform.CLIENT_SECRET);
		adapter.standingOf(user);

		platform.revokeIssuedToken();

		assertThat(adapter.standingOf(user)).isEqualTo(UserStanding.ACTIVE);
		assertThat(platform.tokensIssued()).isEqualTo(2);
		assertThat(platform.authorizationHeaders()).containsExactly("Bearer service-token-1", "Bearer service-token-1",
				"Bearer service-token-2");
	}

	@Test
	void aFailingUserServiceIsReportedAsUnavailableNotAsAnUnknownUser() {
		platform.user(user.toString(), "ACTIVE");
		platform.failUserLookupsWith(500);

		assertThatExceptionOfType(UserDirectoryUnavailableException.class)
				.isThrownBy(() -> adapter(StubPlatform.CLIENT_SECRET).standingOf(user))
				.satisfies(ex -> assertThat(ex.code()).isEqualTo("user-directory-unavailable"));
	}

	@Test
	void wrongOrMissingClientCredentialsAreReportedAsUnavailable() {
		platform.user(user.toString(), "ACTIVE");

		assertThatExceptionOfType(UserDirectoryUnavailableException.class)
				.isThrownBy(() -> adapter("wrong-secret").standingOf(user));
		assertThatExceptionOfType(UserDirectoryUnavailableException.class).isThrownBy(() -> adapter("").standingOf(user));
		assertThat(platform.authorizationHeaders()).isEmpty();
	}

	@Test
	void anUnreachablePlatformIsReportedAsUnavailable() {
		var adapter = adapter(properties("http://localhost:1", StubPlatform.CLIENT_SECRET, false));

		assertThatExceptionOfType(UserDirectoryUnavailableException.class).isThrownBy(() -> adapter.standingOf(user));
	}

	@Test
	void serviceNamesAreResolvedThroughTheLoadBalancerWhenItIsOn() {
		platform.user(user.toString(), "ACTIVE");
		var seenHosts = new java.util.ArrayList<String>();
		String stubAuthority = java.net.URI.create(platform.url()).getAuthority();
		// Stands in for the registry lookup: notes the service name and sends the call to the stub.
		org.springframework.http.client.ClientHttpRequestInterceptor resolver = (request, body, execution) -> {
			seenHosts.add(request.getURI().getHost());
			var resolved = java.net.URI.create(request.getURI().toString().replace(request.getURI().getAuthority(),
					stubAuthority));
			return execution.execute(new org.springframework.http.client.support.HttpRequestWrapper(request) {
				@Override
				public java.net.URI getURI() {
					return resolved;
				}
			}, body);
		};
		var properties = new PlatformDirectoryProperties("http://user-service", "http://auth-service",
				StubPlatform.CLIENT_ID, StubPlatform.CLIENT_SECRET, true, Duration.ofSeconds(2), Duration.ofSeconds(5));
		var tokens = new ServiceTokenProvider(
				PlatformDirectoryConfiguration.client(properties, resolver, properties.authServiceUrl()), properties);
		var adapter = new UserServiceDirectoryAdapter(
				PlatformDirectoryConfiguration.client(properties, resolver, properties.userServiceUrl()), tokens);

		assertThat(adapter.standingOf(user)).isEqualTo(UserStanding.ACTIVE);
		assertThat(seenHosts).containsExactly("auth-service", "user-service");
	}

	@Test
	void turningTheLoadBalancerOnWithoutOneIsRefusedAtStartup() {
		var properties = properties("http://user-service", StubPlatform.CLIENT_SECRET, true);

		org.assertj.core.api.Assertions.assertThatIllegalStateException()
				.isThrownBy(() -> PlatformDirectoryConfiguration.client(properties, null, properties.userServiceUrl()));
	}

	@Test
	void thePropertiesNeverPrintTheSecret() {
		assertThat(properties(platform.url(), "s3cret-value", false)).asString().doesNotContain("s3cret-value")
				.contains("video-service");
	}

	private UserServiceDirectoryAdapter adapter(String clientSecret) {
		return adapter(properties(platform.url(), clientSecret, false));
	}

	private static UserServiceDirectoryAdapter adapter(PlatformDirectoryProperties properties) {
		var tokens = new ServiceTokenProvider(
				PlatformDirectoryConfiguration.client(properties, null, properties.authServiceUrl()), properties);
		return new UserServiceDirectoryAdapter(
				PlatformDirectoryConfiguration.client(properties, null, properties.userServiceUrl()), tokens);
	}

	private static PlatformDirectoryProperties properties(String url, String clientSecret, boolean loadBalanced) {
		return new PlatformDirectoryProperties(url, url, StubPlatform.CLIENT_ID, clientSecret, loadBalanced,
				Duration.ofSeconds(2), Duration.ofSeconds(5));
	}
}
