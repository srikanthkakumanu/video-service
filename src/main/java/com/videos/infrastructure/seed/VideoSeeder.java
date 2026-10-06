package com.videos.infrastructure.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import com.videos.application.SeedVideos;
import com.videos.application.SeedVideos.SeedVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the seed file and hands it to {@link SeedVideos} once the application has started and
 * the schema is migrated. Everything is loaded in one transaction: a file with an invalid entry
 * loads nothing and stops the start, so a broken seed file is noticed at once.
 */
@Component
@ConditionalOnProperty(name = "videos.seed.enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
class VideoSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(VideoSeeder.class);

	record VideosFile(List<SeedVideo> videos) {
	}

	private final SeedProperties properties;
	private final SeedVideos seedVideos;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transaction;

	VideoSeeder(SeedProperties properties, SeedVideos seedVideos, ObjectMapper objectMapper,
			TransactionTemplate transaction) {
		this.properties = properties;
		this.seedVideos = seedVideos;
		this.objectMapper = objectMapper;
		this.transaction = transaction;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<SeedVideo> videos = read();
		Integer added = transaction.execute(status -> seedVideos.handle(videos));
		log.info("Video seed: {} of {} videos added; the rest were already present", added, videos.size());
	}

	private List<SeedVideo> read() {
		try (InputStream in = properties.videos().getInputStream()) {
			return objectMapper.readValue(in, VideosFile.class).videos();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read seed file " + properties.videos(), ex);
		}
	}
}
