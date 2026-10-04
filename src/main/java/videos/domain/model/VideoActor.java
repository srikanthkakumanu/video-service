package videos.domain.model;

import java.util.Objects;
import java.util.UUID;

public record VideoActor(UUID userId, boolean managesCatalog) {
    public VideoActor {
        Objects.requireNonNull(userId, "User identity is required");
    }
}
