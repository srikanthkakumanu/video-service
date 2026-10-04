package videos.infrastructure.web.facade;
import videos.domain.model.VideoQuery;
import videos.domain.port.in.VideoCatalog;
import videos.infrastructure.web.dto.VideoDTO;
import videos.infrastructure.web.dto.VideoRequest;
import videos.infrastructure.web.mapper.VideoMapper;
import videos.infrastructure.security.CurrentVideoActor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.UUID;

@Service
public class VideoServiceImpl implements VideoService {
    private final VideoCatalog catalog;
    private final VideoMapper mapper;
    private final CurrentVideoActor actor;
    public VideoServiceImpl(VideoCatalog catalog, VideoMapper mapper, CurrentVideoActor actor) {
        this.catalog = catalog; this.mapper = mapper; this.actor = actor;
    }
    @Override public VideoDTO save(VideoRequest dto) { return mapper.toDTO(catalog.save(dto.id(), mapper.toChanges(dto), actor.get())); }
    @Override public VideoDTO delete(UUID id) { return mapper.toDTO(catalog.delete(id, actor.get())); }
    @Override public VideoDTO complete(UUID id) { return mapper.toDTO(catalog.complete(id, actor.get())); }
    @Override public VideoDTO findById(UUID id) { return mapper.toDTO(catalog.findById(id)); }
    @Override public VideoDTO findByTitle(String title) { return mapper.toDTO(catalog.findByTitle(title)); }
    @Override public Page<VideoDTO> findAll(PageRequest request) {
        var page = catalog.find(new VideoQuery(null, null, null, request.getPageNumber(), request.getPageSize()));
        return new PageImpl<>(page.content().stream().map(mapper::toDTO).toList(), request, page.total());
    }
    @Override public List<VideoDTO> findAllWithFilters(UUID id, String title, Boolean completed) {
        return catalog.find(new VideoQuery(id, title, completed, null, 20)).content().stream().map(mapper::toDTO).toList();
    }
}
