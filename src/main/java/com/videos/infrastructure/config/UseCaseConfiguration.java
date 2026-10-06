package com.videos.infrastructure.config;

import java.time.Clock;

import com.videos.application.CompleteVideo;
import com.videos.application.CreateVideo;
import com.videos.application.DeleteVideo;
import com.videos.application.GetVideo;
import com.videos.application.SearchVideos;
import com.videos.application.SeedVideos;
import com.videos.application.TransferVideo;
import com.videos.application.UpdateVideo;
import com.videos.domain.port.UserDirectoryPort;
import com.videos.domain.port.VideoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the use cases. They are plain classes with no framework annotations, so the application
 * layer depends on nothing but the domain.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	CreateVideo createVideo(VideoRepository videos, UserDirectoryPort directory, Clock clock) {
		return new CreateVideo(videos, directory, clock);
	}

	@Bean
	UpdateVideo updateVideo(VideoRepository videos, Clock clock) {
		return new UpdateVideo(videos, clock);
	}

	@Bean
	CompleteVideo completeVideo(VideoRepository videos, Clock clock) {
		return new CompleteVideo(videos, clock);
	}

	@Bean
	TransferVideo transferVideo(VideoRepository videos, UserDirectoryPort directory, Clock clock) {
		return new TransferVideo(videos, directory, clock);
	}

	@Bean
	DeleteVideo deleteVideo(VideoRepository videos) {
		return new DeleteVideo(videos);
	}

	@Bean
	GetVideo getVideo(VideoRepository videos) {
		return new GetVideo(videos);
	}

	@Bean
	SearchVideos searchVideos(VideoRepository videos) {
		return new SearchVideos(videos);
	}

	@Bean
	SeedVideos seedVideos(VideoRepository videos, Clock clock) {
		return new SeedVideos(videos, clock);
	}
}
