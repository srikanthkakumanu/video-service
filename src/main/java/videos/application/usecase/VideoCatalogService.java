package videos.application.usecase;

import videos.domain.model.*;
import videos.domain.port.in.VideoCatalog;
import videos.domain.port.out.VideoStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VideoCatalogService implements VideoCatalog {
    private final VideoStore store;
    public VideoCatalogService(VideoStore store) { this.store = store; }

    @Override @Transactional
    public Video save(UUID id, VideoChanges changes, VideoActor actor) {
        return store.save(id == null ? Video.create(changes, actor) : findById(id).revise(changes, actor));
    }
    @Override @Transactional
    public Video delete(UUID id, VideoActor actor) {
        var video = findById(id);
        video.requireWriteAccess(actor);
        store.delete(id);
        return video;
    }
    @Override public Video findById(UUID id) {
        return store.findById(id).orElseThrow(() -> new VideoNotFoundException("Video %s does not exist".formatted(id)));
    }
    @Override public Video findByTitle(String title) {
        return store.findByTitle(title).orElseThrow(() -> new VideoNotFoundException("Video title does not exist"));
    }
    @Override public VideoPage find(VideoQuery query) { return store.find(query); }
    @Override @Transactional
    public Video complete(UUID id, VideoActor actor) {
        return store.save(findById(id).revise(new VideoChanges(null, null, null, null, true), actor));
    }
}
