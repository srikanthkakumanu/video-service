package com.videos.infrastructure.persistence;

import com.videos.domain.model.Paging;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Pieces the two search queries share. */
final class Queries {

	private static final char ESCAPE = '\\';

	private Queries() {
	}

	/** Case-insensitive "contains", with the caller's text taken literally. */
	static Predicate contains(CriteriaBuilder cb, Expression<String> field, String text) {
		String literal = text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return cb.like(cb.lower(field), "%" + literal + "%", ESCAPE);
	}

	/** The ID is a tie-breaker, so pages do not overlap when the sorted values repeat. */
	static PageRequest page(Paging paging) {
		Sort.Direction direction = paging.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
		return PageRequest.of(paging.page(), paging.size(), Sort.by(direction, paging.sortBy()).and(Sort.by("id")));
	}
}
