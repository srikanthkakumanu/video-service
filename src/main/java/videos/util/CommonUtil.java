package videos.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import videos.exception.ExceptionInfo;
import videos.exception.VideoServiceException;

import java.time.ZonedDateTime;
import java.util.*;

public class CommonUtil {

    public static ResponseEntity<Object> buildErrorResponse(Exception re, WebRequest request) {

        Map<String, List<Object>> body = new HashMap<>();
        List<Object> errors = new ArrayList<>();

        ExceptionInfo info = switch (re) {
            case VideoServiceException use ->
                    buildExceptionInfo(use.getEntityName(), use.getStatus().value(),
                            use.getStatus(), use.getMessage(), ZonedDateTime.now(),
                            ((ServletWebRequest) request).getRequest().getRequestURI());
            default ->
                    buildExceptionInfo("video", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected Video Service Error",
                            ZonedDateTime.now(), ((ServletWebRequest) request).getRequest().getRequestURI());
        };
        errors.add(info);
        body.put("errors", errors);
        return new ResponseEntity<>(body, info.status());
    }

    public static ExceptionInfo buildExceptionInfo(String entityName,
                                                   int code,
                                                   HttpStatus status,
                                                   String message,
                                                   ZonedDateTime timestamp, String path) {
        return new ExceptionInfo(UUID.randomUUID().toString(), entityName, code, status, message, ZonedDateTime.now(), path);
    }

}
