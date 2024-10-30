package videos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import videos.domain.Video;
import videos.dto.VideoDTO;
import videos.exception.VideoServiceException;
import videos.mapper.VideoMapper;
import videos.repository.VideosRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public VideoDTO save(VideoDTO dto) {
        log.debug("save: [{}]", dto.toString());

        if (Objects.nonNull(dto.getId())) {
            Optional<Video> foundOptional = repository.findById(dto.getId());

            Optional<VideoDTO> updated = foundOptional.map(found -> {
                if (Objects.nonNull(dto.getTitle()))
                    found.setTitle(dto.getTitle());
                if (Objects.nonNull(dto.getDescription()))
                    found.setDescription(dto.getDescription());
                if (Objects.nonNull(dto.getUserId()))
                    found.setUserId(dto.getUserId());
                if (Objects.nonNull(dto.getUserName()))
                    found.setUserName(dto.getUserName());
                if (Objects.nonNull(dto.getCompleted()))
                    found.setCompleted(dto.getCompleted());

                return mapper.toDTO(repository.save(found));
            });

            if (updated.isPresent())
                return updated.get();
        }

        Video saved = repository.save(mapper.toDomain(dto));
        log.info("Generated Id after saving Video: {}", saved.getId());
        return mapper.toDTO(saved);
    }

    @Override
    public VideoDTO delete(UUID id) {
        log.debug("delete: [Id: {}]", id);

        Video found = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("Video with id {} not found", id);
                    return new VideoServiceException("id", HttpStatus.NOT_FOUND, "Video does not exist");
                });

        repository.delete(found);
        return mapper.toDTO(found);

    }

    @Override
    public List<VideoDTO> findAll() {
        log.debug("findAll() called");
        List<Video> videos = repository.findAll();
        log.debug("videos: length: {}", videos.size());

        return videos.stream().map(mapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public VideoDTO findById(UUID id) {
        log.debug("findById: [Id: {}]", id);

        Video found = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("Video with id {} not found", id);
                    return new VideoServiceException("id", HttpStatus.NOT_FOUND, "Video does not exist");
                });

        return mapper.toDTO(found);

    }

    @Override
    public VideoDTO findByTitle(String title) {
        log.debug("findByTitle: [title: {}]", title);

        Video found =
                repository.findByTitle(title)
                        .orElseThrow(() -> {
                            log.error(String.format("title with %s does not exist", title));
                            return new VideoServiceException("title",
                                    HttpStatus.NOT_FOUND, "title does not exist");
                        });
        return mapper.toDTO(found);
    }
}
