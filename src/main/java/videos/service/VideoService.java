package videos.service;

import videos.dto.VideoDTO;

import java.util.List;
import java.util.UUID;

public interface VideoService {

    VideoDTO save (VideoDTO dto);
    VideoDTO delete (UUID id);
    List<VideoDTO> findAll ();
    VideoDTO findById (UUID id);
    VideoDTO findByTitle (String title);
}
