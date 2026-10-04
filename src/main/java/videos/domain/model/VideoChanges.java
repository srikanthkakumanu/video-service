package videos.domain.model;

import java.util.UUID;
public record VideoChanges(String title, String description, UUID userId, String userName, Boolean completed) {}
