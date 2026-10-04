package videos.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import videos.infrastructure.persistence.entity.Video;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideosRepository extends JpaRepository<Video, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Video> {
    Optional<Video> findByTitle(String title);
}
