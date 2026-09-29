package com.notetaker.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.notetaker.model.ErrorResponse;
import com.notetaker.security.UserIdMissingException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({
      IllegalArgumentException.class,
      MethodArgumentNotValidException.class,
      ConstraintViolationException.class,
      HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class
  })
  @ResponseStatus(BAD_REQUEST)
  public ErrorResponse handleBadRequest(Exception exception) {
    return createExceptionBody(exception, BAD_REQUEST);
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(NOT_FOUND)
  public ErrorResponse handleNotFound(Exception exception) {
    return createExceptionBody(exception, NOT_FOUND);
  }

  @ExceptionHandler(ForbiddenException.class)
  @ResponseStatus(FORBIDDEN)
  public ErrorResponse handleForbidden(Exception exception) {
    return createExceptionBody(exception, FORBIDDEN);
  }

  @ExceptionHandler({AuthenticationException.class, UserIdMissingException.class})
  @ResponseStatus(UNAUTHORIZED)
  public ErrorResponse handleUnauthorized(Exception exception) {
    return createExceptionBody(exception, UNAUTHORIZED);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  @ResponseStatus(CONFLICT)
  public ErrorResponse handleConflict(Exception exception) {
    return createExceptionBody(exception, CONFLICT);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(INTERNAL_SERVER_ERROR)
  public ErrorResponse handleInternalServerError(Exception exception) {
    log.error("Request failed due to unexpected exception", exception);
    return createExceptionBody(exception, INTERNAL_SERVER_ERROR);
  }

  private ErrorResponse createExceptionBody(Exception exception, HttpStatus status) {
    log.debug("Handled HTTP {} for {}", status.value(), exception.getClass().getSimpleName());
    return ErrorResponse.builder()
        .error(status.name())
        .message(exception.getMessage())
        .statusCode(status.value())
        .timestamp(Instant.now())
        .requestId(UUID.randomUUID().toString())
        .build();
  }
}
