package videos.infrastructure.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VideoRequest(UUID id, @Size(min = 1, max = 30) String title,
                           @Size(max = 100) String description, UUID userId,
                           @Size(max = 255) String userName, Boolean completed) {}
