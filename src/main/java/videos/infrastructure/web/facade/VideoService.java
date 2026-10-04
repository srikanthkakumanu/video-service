package videos.infrastructure.web.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import videos.infrastructure.web.dto.VideoDTO;
import videos.infrastructure.web.dto.VideoRequest;

import java.util.List;
import java.util.UUID;

public interface VideoService {
    VideoDTO save (VideoRequest dto);
    VideoDTO delete (UUID id);
    VideoDTO complete(UUID id);
    Page<VideoDTO> findAll (PageRequest pageRequest);
    VideoDTO findById (UUID id);
    public VideoDTO findByTitle (String title);
    List<VideoDTO> findAllWithFilters (UUID id, String title, Boolean completed);
}
