package videos.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Entity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "tbl_video")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Video extends BaseEntity {

    @Valid
    @Size(min = 1, max = 30, message = "title must be between 1 and 30 characters")
    private String title;

    @Size(min = 1, max = 100, message = "description must be between 1 and 100 characters")
    private String description;

    private UUID userId;
    private String userName;

    private Boolean completed;
}
