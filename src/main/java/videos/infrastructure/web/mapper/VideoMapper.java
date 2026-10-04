package videos.infrastructure.web.mapper;
import videos.domain.model.Video;
import videos.domain.model.VideoChanges;
import videos.infrastructure.web.dto.VideoDTO;
import videos.infrastructure.web.dto.VideoRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface VideoMapper {
    VideoDTO toDTO(Video domain);
    VideoChanges toChanges(VideoRequest dto);
    default java.time.LocalDateTime toLocalDateTime(java.time.Instant value) {
        return value == null ? null : java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
