package com.videos.infrastructure.platform;

import com.videos.domain.exception.UserDirectoryUnavailableException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.UserStanding;
import com.videos.domain.port.UserDirectoryPort;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Asks user-service about a user, as this service (not on behalf of the caller). Users are
 * managed only there; nothing about them is stored here.
 */
class UserServiceDirectoryAdapter implements UserDirectoryPort {

	record UserResponse(String id, String status) {
	}

	private final RestClient userService;
	private final ServiceTokenProvider tokens;

	UserServiceDirectoryAdapter(RestClient userService, ServiceTokenProvider tokens) {
		this.userService = userService;
		this.tokens = tokens;
	}

	@Override
	public UserStanding standingOf(OwnerId userId) {
		try {
			try {
				return lookUp(userId);
			}
			catch (HttpClientErrorException.Unauthorized ex) {
				// The cached token was refused, for example after a signing-key change: get a new one once.
				tokens.invalidate();
				return lookUp(userId);
			}
		}
		catch (RuntimeException ex) {
			throw new UserDirectoryUnavailableException("The user directory could not be asked about a user", ex);
		}
	}

	private UserStanding lookUp(OwnerId userId) {
		try {
			UserResponse user = userService.get().uri("/api/v1/users/{id}", userId.value())
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve().body(UserResponse.class);
			return user != null && "ACTIVE".equals(user.status()) ? UserStanding.ACTIVE : UserStanding.INACTIVE;
		}
		catch (HttpClientErrorException.NotFound ex) {
			return UserStanding.UNKNOWN;
		}
	}
}
