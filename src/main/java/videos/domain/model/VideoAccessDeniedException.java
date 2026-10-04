package videos.domain.model;

public class VideoAccessDeniedException extends RuntimeException {
    public VideoAccessDeniedException(String message) {
        super(message);
    }
}
