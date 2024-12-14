package videos.exception;

import org.springframework.http.HttpStatus;
import java.time.ZonedDateTime;

public record ExceptionInfo(String guid,
                            String entityName,
                            Integer code,
                            HttpStatus status,
                            String message,
                            ZonedDateTime timestamp,
                            String path) {}
