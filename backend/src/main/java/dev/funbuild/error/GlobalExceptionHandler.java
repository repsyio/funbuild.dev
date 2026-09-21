package dev.funbuild.error;

import io.repsy.core.error_handling.utils.ErrorUtils;
import io.repsy.core.response.dtos.RestResponse;
import io.repsy.core.response.services.RestResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final RestResponseFactory responses;

  public GlobalExceptionHandler(RestResponseFactory responses) {
    this.responses = responses;
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<RestResponse<Object>> handleNotFound(NotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<RestResponse<Object>> handleConflict(ConflictException ex) {
    return error(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
  public ResponseEntity<RestResponse<Object>> handleForbidden(RuntimeException ex) {
    return error(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<RestResponse<Object>> handleUnauthorized(UnauthorizedException ex) {
    return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<RestResponse<Object>> handleBadRequest(IllegalArgumentException ex) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<RestResponse<Object>> handlePathVariableTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return error(HttpStatus.NOT_FOUND, "Resource not found");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RestResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    return ResponseEntity.badRequest().body(responses.error("validation.failed", fieldErrors));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<RestResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
    String diagnostic = ErrorUtils.exceptionToString(ex, request);
    LOGGER.error(diagnostic);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error");
  }

  private ResponseEntity<RestResponse<Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(responses.error(message == null ? "api.error" : message));
  }
}
