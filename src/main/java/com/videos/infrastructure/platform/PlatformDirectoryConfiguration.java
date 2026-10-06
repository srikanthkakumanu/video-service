package com.videos.infrastructure.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP clients for auth-service and user-service. They are built here and kept private to
 * this adapter: publishing a load-balanced {@code RestClient.Builder} bean would also be picked
 * up by the registry client, which must reach the registry by its real address.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformDirectoryProperties.class)
class PlatformDirectoryConfiguration {

	@Bean
	ServiceTokenProvider serviceTokenProvider(PlatformDirectoryProperties properties,
			ObjectProvider<LoadBalancerInterceptor> loadBalancer) {
		return new ServiceTokenProvider(client(properties, loadBalancer.getIfAvailable(), properties.authServiceUrl()),
				properties);
	}

	@Bean
	UserServiceDirectoryAdapter userServiceDirectoryAdapter(PlatformDirectoryProperties properties,
			ObjectProvider<LoadBalancerInterceptor> loadBalancer, ServiceTokenProvider tokens) {
		return new UserServiceDirectoryAdapter(
				client(properties, loadBalancer.getIfAvailable(), properties.userServiceUrl()), tokens);
	}

	/**
	 * With {@code loadBalanced} on, the host of {@code baseUrl} is a service name resolved through
	 * the registry; with it off, the address is called as it is.
	 */
	static RestClient client(PlatformDirectoryProperties properties, ClientHttpRequestInterceptor loadBalancer,
			String baseUrl) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory).baseUrl(baseUrl);
		if (properties.loadBalanced()) {
			if (loadBalancer == null) {
				throw new IllegalStateException(
						"platform.directory.load-balanced is on but no client-side load balancer is available");
			}
			builder.requestInterceptor(loadBalancer);
		}
		return builder.build();
	}
}
