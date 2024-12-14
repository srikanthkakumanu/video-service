package videos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import videos.domain.Video;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideosRepository extends JpaRepository<Video, UUID> {
    Optional<Video> findByTitle(String title);
}
