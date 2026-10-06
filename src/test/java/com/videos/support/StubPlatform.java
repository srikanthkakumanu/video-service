package com.videos.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for auth-service's service-token endpoint and user-service's user lookup, with the
 * same paths and response shapes the real services have.
 */
public final class StubPlatform {

	public static final String CLIENT_ID = "video-service";
	public static final String CLIENT_SECRET = "stub-client-secret";

	private final HttpServer server;
	private final Map<String, String> userStatusById = new ConcurrentHashMap<>();
	private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
	private final AtomicInteger tokensIssued = new AtomicInteger();
	private volatile String currentToken;
	private volatile int userServiceFailure;
	private volatile long tokenLifetimeSeconds = 300;

	public StubPlatform() {
		try {
			this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/api/v1/auth/service-token", this::issueToken);
		server.createContext("/api/v1/users/", this::lookUpUser);
		server.start();
	}

	public String url() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	public void user(String id, String status) {
		userStatusById.put(id, status);
	}

	/** Makes user-service answer every lookup with this status code; 0 restores normal behaviour. */
	public void failUserLookupsWith(int status) {
		this.userServiceFailure = status;
	}

	/** Forgets the token it issued, as a signing-key change would: the next call with it gets 401. */
	public void revokeIssuedToken() {
		this.currentToken = null;
	}

	public void tokenLifetimeSeconds(long seconds) {
		this.tokenLifetimeSeconds = seconds;
	}

	public int tokensIssued() {
		return tokensIssued.get();
	}

	public List<String> authorizationHeaders() {
		return authorizationHeaders;
	}

	public void reset() {
		userStatusById.clear();
		authorizationHeaders.clear();
		userServiceFailure = 0;
		tokenLifetimeSeconds = 300;
	}

	public void stop() {
		server.stop(0);
	}

	private void issueToken(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		if (!body.contains("\"" + CLIENT_ID + "\"") || !body.contains("\"" + CLIENT_SECRET + "\"")) {
			respond(exchange, 401, "{\"code\":\"invalid-credentials\"}");
			return;
		}
		currentToken = "service-token-" + tokensIssued.incrementAndGet();
		respond(exchange, 200, "{\"accessToken\":\"" + currentToken + "\",\"tokenType\":\"Bearer\",\"expiresIn\":"
				+ tokenLifetimeSeconds + ",\"refreshExpiresIn\":0,\"scope\":\"profile\"}");
	}

	private void lookUpUser(HttpExchange exchange) throws IOException {
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		authorizationHeaders.add(String.valueOf(authorization));
		if (userServiceFailure != 0) {
			respond(exchange, userServiceFailure, "{\"code\":\"internal-error\"}");
			return;
		}
		if (currentToken == null || !("Bearer " + currentToken).equals(authorization)) {
			respond(exchange, 401, "{\"code\":\"unauthorized\"}");
			return;
		}
		String id = exchange.getRequestURI().getPath().substring("/api/v1/users/".length());
		String status = userStatusById.get(id);
		if (status == null) {
			respond(exchange, 404, "{\"code\":\"user-not-found\"}");
			return;
		}
		respond(exchange, 200, "{\"id\":\"" + id + "\",\"username\":\"someone\",\"status\":\"" + status + "\"}");
	}

	private static void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", status < 400 ? "application/json" : "application/problem+json");
		exchange.sendResponseHeaders(status, body.length);
		try (var out = exchange.getResponseBody()) {
			out.write(body);
		}
	}
}
