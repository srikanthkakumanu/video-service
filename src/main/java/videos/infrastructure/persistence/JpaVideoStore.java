package videos.infrastructure.persistence;

import videos.domain.model.*;
import videos.domain.port.out.VideoStore;
import videos.infrastructure.persistence.repository.VideosRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaVideoStore implements VideoStore {
    private final VideosRepository repository;
    private final VideoPersistenceMapper mapper;
    public JpaVideoStore(VideosRepository repository, VideoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }
    @Override public Video save(Video video) { return mapper.toDomain(repository.save(mapper.toEntity(video))); }
    @Override public Optional<Video> findById(UUID id) { return repository.findById(id).map(mapper::toDomain); }
    @Override public Optional<Video> findByTitle(String title) { return repository.findByTitle(title).map(mapper::toDomain); }
    @Override public void delete(UUID id) { repository.deleteById(id); }
    @Override public VideoPage find(VideoQuery query) {
        Specification<videos.infrastructure.persistence.entity.Video> filter = (root, criteria, cb) -> cb.conjunction();
        if (query.id() != null) { filter = filter.and((root, criteria, cb) -> cb.equal(root.get("id"), query.id())); }
        if (query.title() != null) { filter = filter.and((root, criteria, cb) -> cb.equal(cb.lower(root.get("title")), query.title().toLowerCase(java.util.Locale.ROOT))); }
        if (query.completed() != null) { filter = filter.and((root, criteria, cb) -> cb.equal(root.get("completed"), query.completed())); }
        if (query.page() == null) {
            var results = repository.findAll(filter, Sort.by("id"));
            return new VideoPage(results.stream().map(mapper::toDomain).toList(), results.size());
        }
        var page = repository.findAll(filter, PageRequest.of(query.page(), query.size(), Sort.by("id")));
        return new VideoPage(page.getContent().stream().map(mapper::toDomain).toList(), page.getTotalElements());
    }
}
