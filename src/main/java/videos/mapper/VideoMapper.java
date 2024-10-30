package videos.mapper;

import org.mapstruct.*;
import videos.domain.Video;
import videos.dto.VideoDTO;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VideoMapper {
    public VideoDTO toDTO(Video domain);
    public Video toDomain(VideoDTO dto);

    @Mapping(target = "created", source = "created", qualifiedByName = "localDateTimeToTimestamp")
    @Mapping(target = "updated", source = "updated", qualifiedByName = "localDateTimeToTimestamp")
    public Video merge(VideoDTO from, @MappingTarget Video to);

    @Named("localDateTimeToTimestamp")
    default Timestamp localDateTimeToTimestamp(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Timestamp.valueOf(localDateTime);
    }
}
