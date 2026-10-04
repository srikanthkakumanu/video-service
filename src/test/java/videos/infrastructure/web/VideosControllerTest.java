package videos.infrastructure.web;

import videos.infrastructure.security.SecurityConfig;
import videos.infrastructure.web.facade.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = VideosController.class, properties = {
    "spring.cloud.vault.enabled=false", "spring.cloud.config.enabled=false", "spring.config.import="
})
@Import(SecurityConfig.class)
class VideosControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VideoService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void list_withoutToken_shouldReturnUnauthorized() throws Exception {
        mvc.perform(get("/api/videos")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test
    void filter_withToken_shouldBindCriteria() throws Exception {
        when(service.findAllWithFilters(null, "Example", true)).thenReturn(List.of());
        mvc.perform(get("/api/videos/filter").param("title", "Example").param("completed", "true").with(jwt()))
            .andExpect(status().isOk());
        verify(service).findAllWithFilters(null, "Example", true);
    }
}
