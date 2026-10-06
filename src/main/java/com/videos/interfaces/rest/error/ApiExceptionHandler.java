package com.videos.interfaces.rest.error;

import java.util.List;
import java.util.Map;

import com.videos.domain.exception.DomainException;
import com.videos.domain.exception.InvalidValueException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to a problem detail. Failures of the platform behind this service and
 * unexpected exceptions are logged here and answered with a generic message, so nothing internal
 * reaches the caller.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
			Map.entry("invalid-value", HttpStatus.BAD_REQUEST),
			Map.entry("video-not-found", HttpStatus.NOT_FOUND),
			Map.entry("duplicate-video", HttpStatus.CONFLICT),
			Map.entry("operation-not-permitted", HttpStatus.FORBIDDEN),
			Map.entry("owner-not-eligible", HttpStatus.UNPROCESSABLE_CONTENT),
			Map.entry("user-directory-unavailable", HttpStatus.SERVICE_UNAVAILABLE));

	private final ProblemResponses problems;

	ApiExceptionHandler(ProblemResponses problems) {
		this.problems = problems;
	}

	@ExceptionHandler(InvalidValueException.class)
	ProblemDetail invalidValue(InvalidValueException ex) {
		ProblemDetail problem = problems.problem(HttpStatus.BAD_REQUEST, ex.code(), "The request is not valid");
		problem.setProperty("errors", List.of(Map.of("field", ex.field(), "message", ex.getMessage())));
		return problem;
	}

	@ExceptionHandler(DomainException.class)
	ProblemDetail domain(DomainException ex) {
		HttpStatus status = STATUS_BY_CODE.getOrDefault(ex.code(), HttpStatus.INTERNAL_SERVER_ERROR);
		if (status.is5xxServerError()) {
			log.error("Request failed with {}", ex.code(), ex);
			return problems.problem(status, ex.code(), "The service is temporarily unable to handle the request");
		}
		return problems.problem(status, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
		ProblemDetail problem = problems.problem(HttpStatus.BAD_REQUEST, "invalid-value", "The request is not valid");
		problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList());
		return problem;
	}

	@ExceptionHandler({ ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class })
	ProblemDetail malformed(Exception ex) {
		return problems.problem(HttpStatus.BAD_REQUEST, "invalid-value", "The request is not valid");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail accessDenied(AccessDeniedException ex) {
		return problems.problem(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do this");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail notFound(NoResourceFoundException ex) {
		return problems.problem(HttpStatus.NOT_FOUND, "not-found", "No such resource");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		return problems.problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "The method is not supported here");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
		return problems.problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
				"The content type is not supported");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("Unexpected failure", ex);
		return problems.problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred");
	}
}
