package com.videos.interfaces.security;

import com.videos.interfaces.rest.error.ProblemResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Nothing in this service is usable without logging in: every API request needs a valid platform
 * access token, obtained from auth-service. Only the health probes and the API description are
 * open. The token is validated here as well as at the gateway; gateway-added headers are never
 * trusted.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

	static final String[] PUBLIC_GET = { "/actuator/health", "/actuator/health/**", "/actuator/info",
			"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
			ProblemResponses problems) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
						.authenticationEntryPoint((request, response, ex) -> problems.write(response,
								HttpStatus.UNAUTHORIZED, "unauthorized", "A valid access token is required"))
						.accessDeniedHandler((request, response, ex) -> problems.write(response,
								HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do this")))
				.build();
	}
}
