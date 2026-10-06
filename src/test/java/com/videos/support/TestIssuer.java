package com.videos.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/** Stands in for the platform's issuer: serves a JWKS over HTTP and signs access tokens shaped like the platform's. */
public final class TestIssuer {

	public static final String ISSUER = "http://localhost:8080/realms/platform";
	public static final String AUDIENCE = "video-service";

	private final HttpServer server;
	private final RSAKey signingKey;

	public TestIssuer() {
		try {
			this.signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
			this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		}
		catch (JOSEException | IOException ex) {
			throw new IllegalStateException(ex);
		}
		byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
		server.createContext("/certs", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, jwks.length);
			try (var out = exchange.getResponseBody()) {
				out.write(jwks);
			}
		});
		server.start();
	}

	public String jwkSetUri() {
		return "http://localhost:" + server.getAddress().getPort() + "/certs";
	}

	/** A token for a logged-in user holding the given realm roles and permissions. */
	public String token(String subject, List<String> roles, List<String> permissions) {
		return token(claims -> claims.subject(subject).claim("realm_access", Map.of("roles", roles))
				.claim("permissions", permissions));
	}

	public String token(Consumer<JWTClaimsSet.Builder> customizer) {
		var now = Instant.now();
		var claims = new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.subject(UUID.randomUUID().toString())
				.audience(List.of(AUDIENCE, "api-gateway"))
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
				.jwtID(UUID.randomUUID().toString())
				.claim("typ", "Bearer")
				.claim("preferred_username", "tester");
		customizer.accept(claims);
		try {
			var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
					claims.build());
			jwt.sign(new RSASSASigner(signingKey));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
