package videos.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagementAccessTest.ProbeController.class, properties = {
        "spring.config.import=", "spring.cloud.vault.enabled=false", "spring.cloud.config.enabled=false",
        "spring.docker.compose.enabled=false"
})
@Import({SecurityConfig.class, ManagementAccessTest.ProbeController.class})
class ManagementAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test
    void healthProbesRemainPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/info", "/actuator/loggers", "/api-docs", "/api-docs.yaml", "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html"})
    void managementAndDocumentationRequireAdmin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/actuator/health", "/actuator/health/readiness", "/actuator/info",
                "/actuator/loggers", "/api-docs", "/api-docs.yaml", "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html"})
        String probe() { return "{}"; }
    }
}
