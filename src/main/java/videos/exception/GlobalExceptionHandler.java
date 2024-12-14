package videos.exception;

import java.time.ZonedDateTime;
import java.util.*;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import videos.util.CommonUtil;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {

        Map<String, List<Object>> body = new HashMap<>();
        List<Object> errors = new ArrayList<>();

        log.error("Type mismatch=[Required Type: {}, Given Value: {}, Error Code: {}, Property Name: {}]",
                ex.getRequiredType(),
                ex.getValue(),
                ex.getErrorCode(),
                ex.getPropertyName());

        String fieldName = ex.getPropertyName();
        String errorMessage = String.format("%s type is invalid.", ex.getPropertyName());
        ZonedDateTime timestamp = ZonedDateTime.now();

        ExceptionInfo exInfo =
                CommonUtil.buildExceptionInfo(fieldName,
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST, errorMessage,
                        timestamp, ((ServletWebRequest) request).getRequest().getRequestURI());
        errors.add(exInfo);
        log.error(exInfo.toString());
        body.put("errors", errors);

        return new ResponseEntity<>(body, status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, List<Object>> body = new HashMap<>();
        List<Object> errors = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            ExceptionInfo exInfo =
                    CommonUtil.buildExceptionInfo(((FieldError) error).getField(),
                            HttpStatus.BAD_REQUEST.value(),
                            HttpStatus.BAD_REQUEST,
                            error.getDefaultMessage(),
                            ZonedDateTime.now(), ((ServletWebRequest) request).getRequest().getRequestURI());
            errors.add(exInfo);
            log.error(exInfo.toString());
        });
        body.put("errors", errors);

        return new ResponseEntity<>(body, status);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, List<Object>> body = new HashMap<>();
        List<Object> errors = new ArrayList<>();

        ex.getAllErrors().forEach( e -> {
            String fieldMessage = e.getDefaultMessage();
            String fieldName = ((DefaultMessageSourceResolvable) Objects.requireNonNull(e.getArguments())[0]).getDefaultMessage();
            ExceptionInfo exInfo =
                    CommonUtil.buildExceptionInfo(fieldName,
                            HttpStatus.BAD_REQUEST.value(),
                            HttpStatus.BAD_REQUEST,
                            fieldMessage,
                            ZonedDateTime.now(), ((ServletWebRequest) request).getRequest().getRequestURI());
            errors.add(exInfo);
            log.error(exInfo.toString());
        });

        body.put("errors", errors);

        return new ResponseEntity<>(body, status);

    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {

        Map<String, List<Object>> body = new HashMap<>();
        List<Object> errors = new ArrayList<>();

        if (ex.getCause() instanceof InvalidFormatException ifx) {
            //InvalidFormatException ifx = (InvalidFormatException) ex.getCause();
            if (ifx.getTargetType() != null && ifx.getTargetType().isEnum()) {
                String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
                String errorMessage = String.format("%s is invalid.", ifx.getValue());
                ZonedDateTime timestamp = ZonedDateTime.now();

                ExceptionInfo exInfo =
                        CommonUtil.buildExceptionInfo(fieldName,
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST, errorMessage,
                                timestamp, ((ServletWebRequest) request).getRequest().getRequestURI());
                errors.add(exInfo);
                log.error(exInfo.toString());
            }
        }
        body.put("errors", errors);

        return new ResponseEntity<>(body, status);

    }

    @ExceptionHandler(VideoServiceException.class)
    public ResponseEntity<Object> handleVideoServiceException(VideoServiceException ex, WebRequest request) {
        log.error(ex.toString());
        return CommonUtil.buildErrorResponse(ex, request);
    }

    @ExceptionHandler({Exception.class, RuntimeException.class})
    public ResponseEntity<Object> handleUnknownException(Exception ex, WebRequest request) {
        log.error(ex.toString());
        return CommonUtil.buildErrorResponse(ex, request);
    }
}
