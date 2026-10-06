package com.videos.domain.model;

import java.util.Set;

/** Filters for finding videos; a null filter is not applied. The title filter matches anywhere, ignoring case. */
public record VideoSearch(String title, Boolean completed, OwnerId ownerId, Paging paging) {

	public static final Set<String> SORT_FIELDS = Set.of("title", "createdAt");
	public static final String DEFAULT_SORT = "title";

	public VideoSearch {
		title = title == null || title.isBlank() ? null : title.strip();
	}
}
