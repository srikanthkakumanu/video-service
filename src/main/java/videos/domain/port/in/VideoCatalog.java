package videos.domain.port.in;

import videos.domain.model.*;
import java.util.UUID;
public interface VideoCatalog {
    Video save(UUID id, VideoChanges changes, VideoActor actor);
    Video delete(UUID id, VideoActor actor);
    Video findById(UUID id);
    Video findByTitle(String title);
    VideoPage find(VideoQuery query);
    Video complete(UUID id, VideoActor actor);
}
