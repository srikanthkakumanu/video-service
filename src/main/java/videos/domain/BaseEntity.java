package videos.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

@Data
@NoArgsConstructor
@MappedSuperclass
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed abstract class BaseEntity permits Video {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(value = Types.VARBINARY)
    @Column(columnDefinition = "VARBINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(value = Types.VARBINARY)
    @Column(columnDefinition = "VARBINARY(16)", updatable = true, nullable = true)
    private UUID userId;

    @CreationTimestamp
    @Column(updatable = false, nullable = true)
    private Timestamp created;

    @UpdateTimestamp
    @Column(updatable = true, nullable = true)
    private Timestamp updated;

}
