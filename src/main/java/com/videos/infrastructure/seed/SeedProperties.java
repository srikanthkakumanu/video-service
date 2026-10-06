package com.videos.infrastructure.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Whether the starter set of videos is loaded at startup, and from where. On in development, off
 * elsewhere (see service-configs).
 */
@ConfigurationProperties("videos.seed")
record SeedProperties(@DefaultValue("false") boolean enabled,
		@DefaultValue("classpath:data/videos.json") Resource videos) {
}
