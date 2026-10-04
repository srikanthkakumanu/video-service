package videos.infrastructure.security;

import videos.domain.model.VideoActor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CurrentVideoActor {
    public VideoActor get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AccessDeniedException("A Keycloak bearer identity is required");
        }
        boolean manager = jwt.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_MANAGER"));
        try {
            return new VideoActor(UUID.fromString(jwt.getToken().getSubject()), manager);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Keycloak subject must be a UUID", e);
        }
    }
}
