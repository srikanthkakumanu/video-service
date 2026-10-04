package videos.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Video(UUID id, Instant created, Instant updated, String title, String description,
                    UUID userId, String userName, Boolean completed) {
    public Video {
        if (title == null || title.isBlank() || title.length() > 30) {
            throw new IllegalArgumentException("Video title must contain 1 to 30 characters");
        }
        if (description != null && description.length() > 100) {
            throw new IllegalArgumentException("Video description must not exceed 100 characters");
        }
        title = title.strip();
        completed = Boolean.TRUE.equals(completed);
    }

    public static Video create(VideoChanges changes, VideoActor actor) {
        var owner = actor.managesCatalog() && changes.userId() != null ? changes.userId() : actor.userId();
        return new Video(null, null, null, changes.title(), changes.description(), owner, changes.userName(), changes.completed());
    }

    public Video revise(VideoChanges changes, VideoActor actor) {
        requireWriteAccess(actor);
        var owner = changes.userId() == null ? userId : changes.userId();
        if (!actor.managesCatalog() && !Objects.equals(owner, userId)) {
            throw new VideoAccessDeniedException("Only a manager may transfer video ownership");
        }
        return new Video(id, created, updated, changes.title() == null ? title : changes.title(),
                changes.description() == null ? description : changes.description(), owner,
                changes.userName() == null ? userName : changes.userName(),
                changes.completed() == null ? completed : changes.completed());
    }

    public void requireWriteAccess(VideoActor actor) {
        if (!actor.managesCatalog() && !Objects.equals(userId, actor.userId())) {
            throw new VideoAccessDeniedException("Video may only be changed by its owner or a manager");
        }
    }
}
