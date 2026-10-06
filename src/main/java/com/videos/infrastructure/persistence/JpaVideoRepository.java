package com.videos.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.videos.domain.exception.DuplicateVideoException;
import com.videos.domain.model.OwnerId;
import com.videos.domain.model.PageResult;
import com.videos.domain.model.Title;
import com.videos.domain.model.Video;
import com.videos.domain.model.VideoDetails;
import com.videos.domain.model.VideoId;
import com.videos.domain.model.VideoSearch;
import com.videos.domain.port.VideoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaVideoRepository implements VideoRepository {

	private final SpringDataVideoRepository repository;

	JpaVideoRepository(SpringDataVideoRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public Video save(Video video) {
		VideoDetails details = video.details();
		VideoJpaEntity entity = repository.findById(video.id().value())
				.orElseGet(() -> new VideoJpaEntity(video.id().value(), video.createdAt()));
		entity.apply(details.title().value(), details.description(), video.ownerId().map(OwnerId::value).orElse(null),
				details.completed(), video.updatedAt());
		try {
			return toDomain(repository.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException ex) {
			// The title was taken between the check and the write; the unique constraint is the last word.
			throw new DuplicateVideoException("Another video is already titled '" + details.title() + "'");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Video> findById(VideoId id) {
		return repository.findById(id.value()).map(JpaVideoRepository::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Video> findByTitle(Title title) {
		return repository.findByTitle(title.value()).map(JpaVideoRepository::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean exists(VideoId id) {
		return repository.existsById(id.value());
	}

	@Override
	@Transactional(readOnly = true)
	public PageResult<Video> search(VideoSearch search) {
		Specification<VideoJpaEntity> matching = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (search.title() != null) {
				predicates.add(Queries.contains(cb, root.get("title"), search.title()));
			}
			if (search.completed() != null) {
				predicates.add(cb.equal(root.get("completed"), search.completed()));
			}
			if (search.ownerId() != null) {
				predicates.add(cb.equal(root.get("ownerId"), search.ownerId().value()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
		Page<VideoJpaEntity> page = repository.findAll(matching, Queries.page(search.paging()));
		return new PageResult<>(page.getContent().stream().map(JpaVideoRepository::toDomain).toList(),
				page.getTotalElements(), search.paging().page(), search.paging().size());
	}

	@Override
	@Transactional
	public void delete(VideoId id) {
		repository.deleteById(id.value());
	}

	private static Video toDomain(VideoJpaEntity entity) {
		return Video.rehydrate(new VideoId(entity.getId()),
				new VideoDetails(new Title(entity.getTitle()), entity.getDescription(), entity.isCompleted()),
				entity.getOwnerId() == null ? null : new OwnerId(entity.getOwnerId()), entity.getCreatedAt(),
				entity.getUpdatedAt());
	}
}
