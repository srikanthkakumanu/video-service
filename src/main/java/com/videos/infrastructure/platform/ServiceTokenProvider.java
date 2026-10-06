package com.videos.infrastructure.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Obtains this service's own access token from auth-service (client credentials) and keeps it
 * until shortly before it expires. The token and the secret are never logged.
 */
class ServiceTokenProvider {

	private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);

	record TokenResponse(String accessToken, long expiresIn) {
	}

	private record Cached(String token, Instant usableUntil) {
	}

	private final RestClient authService;
	private final PlatformDirectoryProperties properties;
	private final Clock clock = Clock.systemUTC();
	private volatile Cached cached;

	ServiceTokenProvider(RestClient authService, PlatformDirectoryProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	String token() {
		Cached current = cached;
		if (current != null && clock.instant().isBefore(current.usableUntil())) {
			return current.token();
		}
		return renew();
	}

	/** Drops the cached token, for when the platform has just refused it. */
	void invalidate() {
		cached = null;
	}

	private synchronized String renew() {
		Cached current = cached;
		if (current != null && clock.instant().isBefore(current.usableUntil())) {
			return current.token();
		}
		if (properties.clientId() == null || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
			throw new IllegalStateException("No client credentials are configured for calling the platform");
		}
		TokenResponse response = authService.post().uri("/api/v1/auth/service-token")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("clientId", properties.clientId(), "clientSecret", properties.clientSecret()))
				.retrieve().body(TokenResponse.class);
		if (response == null || response.accessToken() == null) {
			throw new IllegalStateException("The platform returned no service token");
		}
		cached = new Cached(response.accessToken(),
				clock.instant().plusSeconds(response.expiresIn()).minus(SAFETY_MARGIN));
		return response.accessToken();
	}
}
