package videos.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@MappedSuperclass
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(updatable = false, nullable = true)
    private Instant created;

    @UpdateTimestamp
    @Column(updatable = true, nullable = true)
    private Instant updated;

}
