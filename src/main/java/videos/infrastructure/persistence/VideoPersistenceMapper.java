package videos.infrastructure.persistence;

import videos.domain.model.Video;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, builder = @org.mapstruct.Builder(disableBuilder = true))
public interface VideoPersistenceMapper {
    Video toDomain(videos.infrastructure.persistence.entity.Video entity);
    videos.infrastructure.persistence.entity.Video toEntity(Video domain);
}
