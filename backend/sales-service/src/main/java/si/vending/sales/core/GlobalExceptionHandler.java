package si.vending.sales.core;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/** Maps every failure to the uniform error body. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Object> handleApi(ApiException e) {
    return respond(e.status(), e.code(), e.getMessage(), null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception e) {
    LOG.error("Unhandled exception", e);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error.", null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    BindingResult result = ex.getBindingResult();
    if (result.getFieldErrors().stream().anyMatch(f -> "typeMismatch".equals(f.getCode()))) {
      return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request parameter.", null);
    }
    List<ApiError.Detail> details =
        result.getAllErrors().stream()
            .map((ObjectError error) ->
                new ApiError.Detail(
                    error instanceof FieldError f ? f.getField() : error.getObjectName(),
                    error.getDefaultMessage()))
            .toList();
    return validation(details);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ApiError.Detail> details =
        ex.getParameterValidationResults().stream()
            .flatMap(r ->
                r.getResolvableErrors().stream()
                    .map(e ->
                        new ApiError.Detail(
                            r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
            .toList();
    return validation(details);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
      return validation(List.of(new ApiError.Detail(unknown.getPropertyName(), "Unknown field.")));
    }
    return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request body.", null);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request parameter.", null);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.", null);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    if (resolved == null || resolved.is5xxServerError()) {
      LOG.error("Unhandled exception", ex);
      return respond(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error.", null);
    }
    String code = resolved == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "BAD_REQUEST";
    return respond(resolved, code, resolved.getReasonPhrase() + ".", null);
  }

  private ResponseEntity<Object> validation(List<ApiError.Detail> details) {
    return respond(
        HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", details);
  }

  private ResponseEntity<Object> respond(
      HttpStatus status, String code, String message, List<ApiError.Detail> details) {
    ApiError body = new ApiError(code, message, MDC.get(TraceIdFilter.MDC_KEY), details);
    return ResponseEntity.status(status).body(body);
  }
}
