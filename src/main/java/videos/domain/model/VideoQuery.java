package videos.domain.model;

import java.util.UUID;
public record VideoQuery(UUID id, String title, Boolean completed, Integer page, int size) {
    public VideoQuery {
        if (page != null && (page < 0 || size < 1 || size > 200)) {
            throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 200");
        }
    }
}
