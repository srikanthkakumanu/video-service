package com.videos.interfaces.rest.error;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds RFC 9457 problem details. Every problem has a stable {@code type} URI and a matching
 * {@code code}; clients switch on those, never on the human-readable text.
 */
@Component
public class ProblemResponses {

	public static final String TYPE_BASE = "https://platform.local/problems/";

	private final ObjectMapper objectMapper;

	public ProblemResponses(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(TYPE_BASE + code));
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return problem;
	}

	/** For failures raised in the security filter chain, before any controller advice applies. */
	public void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem(status, code, detail));
	}
}
