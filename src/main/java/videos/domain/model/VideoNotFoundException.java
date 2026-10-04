package videos.domain.model;

public class VideoNotFoundException extends RuntimeException {
    public VideoNotFoundException(String message) { super(message); }
}
