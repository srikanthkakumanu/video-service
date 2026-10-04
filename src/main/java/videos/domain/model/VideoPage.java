package videos.domain.model;

import java.util.List;
public record VideoPage(List<Video> content, long total) {
    public VideoPage { content = List.copyOf(content); }
}
