package com.yourcompany.agritrade.common.exception;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// **********************************

@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  // --- Các phương thức handle* được override từ ResponseEntityExceptionHandler ---

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              fieldErrors.put(fieldName, errorMessage);
            });
    String errorDetailsMessage =
        fieldErrors.entrySet().stream()
            .map(entry -> String.format("'%s': %s", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));
    log.warn(
        "Validation failed: [{}] for request: {}",
        errorDetailsMessage,
        request.getDescription(false));

    // Tạo ApiResponse với details chứa các lỗi field
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(), // Lấy status code từ HttpStatus
            "Validation Failed", // Message chung
            fieldErrors // Đưa map lỗi field vào details
            );
    // Chuyển đổi HttpStatusCode sang HttpStatus nếu cần, hoặc dùng status trực tiếp nếu là
    // HttpStatus
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message = "Malformed JSON request. Please check the request body format.";
    log.warn(
        "Malformed JSON request: {} for request: {}",
        ex.getMessage(),
        request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            message,
            ex.getMessage()); // Có thể đưa lỗi gốc vào details
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message = ex.getParameterName() + " parameter is missing";
    log.warn(
        "Missing request parameter: {} for request: {}", message, request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, null);
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    StringBuilder builder = new StringBuilder();
    builder.append(ex.getMethod());
    builder.append(" method is not supported for this request. Supported methods are ");
    ex.getSupportedHttpMethods().forEach(t -> builder.append(t).append(" "));
    String message = builder.toString();
    log.warn("Method not supported: {} for request: {}", message, request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), message, null);
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    StringBuilder builder = new StringBuilder();
    builder.append(ex.getContentType());
    builder.append(" media type is not supported. Supported media types are ");
    ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(" "));
    String message = builder.toString();
    log.warn(
        "Media type not supported: {} for request: {}", message, request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), message, null);
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  @Override
  protected ResponseEntity<Object> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String message = "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
    log.warn("No handler found: {} ", message);
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.NOT_FOUND.value(), message, null);
    HttpStatus httpStatus = HttpStatus.valueOf(status.value());
    return new ResponseEntity<>(apiResponse, httpStatus);
  }

  // --- Các phương thức @ExceptionHandler ---

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch( // Đổi kiểu trả về
      MethodArgumentTypeMismatchException ex, WebRequest request) {
    String message =
        String.format(
            "The parameter '%s' of value '%s' could not be converted to type '%s'",
            ex.getName(),
            ex.getValue(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
    log.warn("Type mismatch: {} for request: {}", message, request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, null);
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiResponse<Object>> handleBadRequestException(
      BadRequestException ex, WebRequest request) { // Đổi kiểu trả về
    log.warn(
        "Bad Request (Custom): {} for request: {}", ex.getMessage(), request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(OutOfStockException.class)
  public ResponseEntity<ApiResponse<Object>> handleOutOfStockException(
      OutOfStockException ex, WebRequest request) {
    log.warn(
        "Out Of Stock Exception: {} for request: {}",
        ex.getMessage(),
        request.getDescription(false));
    Map<String, Object> errorDetails = new HashMap<>();
    errorDetails.put("errorCode", "ERR_OUT_OF_STOCK");
    if (ex.getAvailableStock() != null) {
      errorDetails.put("availableStock", ex.getAvailableStock());
    }
    // Tạo ApiResponse với details
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), errorDetails);
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) { // Đổi kiểu trả về
    log.warn(
        "Resource Not Found (Custom): {} for request: {}",
        ex.getMessage(),
        request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
    return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleEntityNotFound(
      EntityNotFoundException ex, WebRequest request) { // Đổi kiểu trả về
    String message = "The requested entity was not found.";
    log.warn(
        "Entity Not Found: {} for request: {}", ex.getMessage(), request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.NOT_FOUND.value(),
            message,
            ex.getMessage()); // Có thể đưa lỗi gốc vào details
    return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(
      BadCredentialsException ex, WebRequest request) { // Đổi kiểu trả về
    String message = "Invalid username or password.";
    log.warn(
        "Authentication Failed: Invalid credentials for request: {}",
        request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), message, null);
    return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
      AccessDeniedException ex, WebRequest request) { // Đổi kiểu trả về
    String message = "You do not have permission to access this resource.";
    log.warn("Access Denied: {} for request: {}", ex.getMessage(), request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.FORBIDDEN.value(),
            message,
            ex.getMessage()); // Có thể đưa lỗi gốc vào details
    return new ResponseEntity<>(apiResponse, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, WebRequest request) { // Đổi kiểu trả về
    String specificCauseMessage = ex.getMostSpecificCause().getMessage();
    String message =
        "Data integrity violation. There might be conflicting data (e.g., duplicate entry).";
    log.error(
        "Data Integrity Violation: {} for request: {}",
        specificCauseMessage,
        request.getDescription(false),
        ex);
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.CONFLICT.value(),
            message,
            specificCauseMessage); // Đưa lỗi cụ thể vào details
    return new ResponseEntity<>(apiResponse, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGlobalException(
      Exception ex, WebRequest request) { // Đổi kiểu trả về
    String message = "An unexpected internal server error occurred. Please try again later.";
    log.error("Unexpected error processing request: {}", request.getDescription(false), ex);
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message,
            ex.getMessage()); // Đưa lỗi gốc vào details
    return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
