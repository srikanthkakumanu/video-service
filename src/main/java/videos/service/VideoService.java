package videos.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import videos.dto.VideoDTO;

import java.util.List;
import java.util.UUID;

public interface VideoService {
    VideoDTO save (VideoDTO dto);
    VideoDTO delete (UUID id);
    Page<VideoDTO> findAll (PageRequest pageRequest);
    VideoDTO findById (UUID id);
    public VideoDTO findByTitle (String title);
    List<VideoDTO> findAllWithFilters (UUID id, String title, Boolean completed);
}
