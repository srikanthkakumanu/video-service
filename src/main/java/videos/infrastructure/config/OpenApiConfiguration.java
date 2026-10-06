package videos.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info().title("Video Service").version("1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer publicGetSecurity() {
        return api -> {
            if (api.getPaths() == null) {
                return;
            }
            api.getPaths().forEach((path, item) -> {
                if ((path.equals("/api/videos/ping") || path.equals("/actuator/health")
                        || path.startsWith("/actuator/health/")) && item.getGet() != null) {
                    item.getGet().setSecurity(List.of());
                }
            });
        };
    }
}
