package videos.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class VideoServiceException extends RuntimeException {
    private final String entityName;
    private final HttpStatus status;
    private final String message;
}
