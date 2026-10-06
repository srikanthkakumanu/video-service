package videos.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OpenApiConfigurationTest {
    private final OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void catalogContract_requiresHttpBearerJwt() {
        var api = configuration.catalogOpenApi();
        var scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(api.getSecurity()).singleElement().satisfies(requirement ->
                assertThat(requirement).containsKey("bearerAuth"));
        assertThat(api.getInfo().getTitle()).isEqualTo("Video Service");
    }

    @Test
    void publicOverride_appliesOnlyToExactPublicGetsAndHealthChildren() {
        var api = configuration.catalogOpenApi().paths(new Paths());
        for (var path : new String[] {"/api/videos/ping", "/actuator/health", "/actuator/health/readiness",
                "/actuator/health/liveness", "/api/videos/ping/private", "/actuator/healthz",
                "/api/videos", "/actuator/info"}) {
            api.getPaths().addPathItem(path, new PathItem().get(new Operation()).post(new Operation()));
        }
        configuration.publicGetSecurity().customise(api);
        for (var path : new String[] {"/api/videos/ping", "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            assertThat(api.getPaths().get(path).getGet().getSecurity()).isEmpty();
            assertThat(api.getPaths().get(path).getPost().getSecurity()).isNull();
        }
        for (var path : new String[] {"/api/videos/ping/private", "/actuator/healthz", "/api/videos", "/actuator/info"}) {
            assertThat(api.getPaths().get(path).getGet().getSecurity()).isNull();
        }
        assertThat(api.getSecurity()).singleElement().satisfies(requirement ->
                assertThat(requirement).containsKey("bearerAuth"));
    }

    @Test
    void publicOverride_handlesDocumentsWithoutPaths() {
        assertThatCode(() -> configuration.publicGetSecurity().customise(new OpenAPI()))
                .doesNotThrowAnyException();
    }
}
