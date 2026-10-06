package com.videos.interfaces.security;

import com.platform.security.claims.AccessTokenClaims;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.VideoActor;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds the domain's view of the caller from the validated platform access token: the user ID
 * is the token subject (the ID user-service manages), and what they may do comes from the
 * permissions auth-service put in the token.
 */
public final class CurrentVideoActor {

	private CurrentVideoActor() {
	}

	public static VideoActor from(Jwt jwt) {
		var claims = AccessTokenClaims.from(jwt.getClaims());
		return new VideoActor(OwnerId.of(claims.subject()), claims.hasPermission(Permissions.VIDEOS_MANAGE));
	}
}
