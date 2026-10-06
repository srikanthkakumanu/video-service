package com.videos.infrastructure.platform;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this service reaches the platform's user and auth services. The addresses are service
 * names resolved through the registry unless {@code loadBalanced} is off. The client secret
 * comes from Vault, never from a file.
 */
@ConfigurationProperties("platform.directory")
record PlatformDirectoryProperties(
		@DefaultValue("http://user-service") String userServiceUrl,
		@DefaultValue("http://auth-service") String authServiceUrl,
		String clientId,
		String clientSecret,
		@DefaultValue("true") boolean loadBalanced,
		@DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout) {

	@Override
	public String toString() {
		return "PlatformDirectoryProperties[userServiceUrl=" + userServiceUrl + ", authServiceUrl=" + authServiceUrl
				+ ", clientId=" + clientId + "]";
	}
}
