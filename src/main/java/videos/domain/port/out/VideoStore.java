package videos.domain.port.out;

import videos.domain.model.*;
import java.util.Optional;
import java.util.UUID;
public interface VideoStore {
    Video save(Video video);
    Optional<Video> findById(UUID id);
    Optional<Video> findByTitle(String title);
    VideoPage find(VideoQuery query);
    void delete(UUID id);
}
