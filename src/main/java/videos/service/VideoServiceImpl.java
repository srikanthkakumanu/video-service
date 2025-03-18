package videos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import videos.domain.Video;
import videos.dto.VideoDTO;
import videos.exception.VideoServiceException;
import videos.mapper.VideoMapper;
import videos.repository.VideosRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class VideoServiceImpl implements VideoService {

    private final VideosRepository repository;
    private final VideoMapper mapper;

    public VideoServiceImpl(VideosRepository repository, VideoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public VideoDTO save(VideoDTO dto) {
        log.debug("save: [{}]", dto.toString());
        Video savable = null;

        if (Objects.nonNull(dto.getId())) {
            Optional<Video> foundOptional = repository.findById(dto.getId());
            savable =
                    foundOptional.map(video -> getVideo(dto, video))
                            .orElseGet(() -> mapper.toDomain(dto));
        }
        log.info("Video to be saved with Id: '{}'", savable.getId());
        return mapper.toDTO(repository.save(savable));
    }

    private static Video getVideo(VideoDTO dto, Video foundVideo) {
        if (Objects.nonNull(dto.getTitle()))
            foundVideo.setTitle(dto.getTitle());
        if (Objects.nonNull(dto.getDescription()))
            foundVideo.setDescription(dto.getDescription());
        if (Objects.nonNull(dto.getUserId()))
            foundVideo.setUserId(dto.getUserId());
        if (Objects.nonNull(dto.getUserName()))
            foundVideo.setUserName(dto.getUserName());
        if (Objects.nonNull(dto.getCompleted()))
            foundVideo.setCompleted(dto.getCompleted());
        return foundVideo;
    }

    @Override
    public VideoDTO delete(UUID id) {
        Video found = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("Video with id '{}' not found", id);
                    return new VideoServiceException("id", HttpStatus.NOT_FOUND, "Video does not exist");
                });

        repository.delete(found);
        log.debug("video with given Id: '{}' is deleted", id);
        return mapper.toDTO(found);

    }

    @Override
    public Page<VideoDTO> findAll(PageRequest pageRequest) {
        log.debug("findAll() called");
        return repository.findAll(pageRequest).map(mapper::toDTO);
    }

    @Override
    public VideoDTO findById(UUID id) {
        log.debug("find video by Id: [Id: {}]", id);

        Video found = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("Video with given id '{}' is not found", id);
                    return new VideoServiceException("id", HttpStatus.NOT_FOUND, "Video does not exist");
                });

        return mapper.toDTO(found);

    }

    public List<VideoDTO> findAllWithFilters(UUID id, String title, Boolean completed) {
        log.debug("find all videos with filters: [id: {}, title: {}, completed: {}]", id, title, completed);

        List<Video> filteredVideos = repository.findAll().stream()
                .filter(video -> (id == null || video.getId().equals(id)) &&
                        (title == null || video.getTitle().equalsIgnoreCase(title)) &&
                        (completed == null || video.getCompleted().equals(completed)))
                .toList();

        if (filteredVideos.isEmpty()) {
            log.error("No videos matched the provided filters");
            throw new VideoServiceException("filterCriteria", HttpStatus.NOT_FOUND, "No videos matched the criteria");
        }

        log.debug("Filtered videos count: {}", filteredVideos.size());
        return filteredVideos.stream().map(mapper::toDTO).toList();
    }

    @Override
    public VideoDTO findByTitle(String title) {
        log.debug("find by title: [title: {}]", title);

        Video found =
                repository.findByTitle(title)
                        .orElseThrow(() -> {
                            log.error("Video with title '{}' does not exist", title);
                            return new VideoServiceException("title",
                                    HttpStatus.NOT_FOUND, "Video with the given title does not exist");
                        });
        return mapper.toDTO(found);
    }
}
