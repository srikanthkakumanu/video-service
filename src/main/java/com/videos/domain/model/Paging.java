package com.videos.domain.model;

import java.util.Set;

import com.videos.domain.exception.InvalidValueException;

/** Which page of a search to return and how it is ordered. */
public record Paging(int page, int size, String sortBy, boolean ascending) {

	public static final int MAX_SIZE = 100;

	public Paging {
		if (page < 0) {
			throw new InvalidValueException("page", "must not be negative");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new InvalidValueException("size", "must be between 1 and " + MAX_SIZE);
		}
	}

	/** Parses {@code field} or {@code field,asc|desc}, allowing only the given fields. */
	public static Paging of(int page, int size, String sort, String defaultField, Set<String> allowedFields) {
		if (sort == null || sort.isBlank()) {
			return new Paging(page, size, defaultField, true);
		}
		String[] parts = sort.strip().split(",");
		String field = parts[0].strip();
		String direction = parts.length > 1 ? parts[1].strip().toLowerCase() : "asc";
		if (parts.length > 2 || !allowedFields.contains(field) || !(direction.equals("asc") || direction.equals("desc"))) {
			throw new InvalidValueException("sort",
					"must be one of " + allowedFields.stream().sorted().toList() + ", optionally followed by ,asc or ,desc");
		}
		return new Paging(page, size, field, direction.equals("asc"));
	}
}
