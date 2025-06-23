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

@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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
        "Lỗi xác thực: [{}] cho request: {}", errorDetailsMessage, request.getDescription(false));

    // Tạo ApiResponse với details chứa các lỗi field
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(), // Lấy status code từ HttpStatus
            "Dữ liệu không hợp lệ", // Message chung
            fieldErrors // Đưa map lỗi field vào details
            );

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
      BadRequestException ex, WebRequest request) {
    log.warn(
        "Yêu cầu không hợp lệ: {}. Request: {}", ex.getMessage(), request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(OutOfStockException.class)
  public ResponseEntity<ApiResponse<Object>> handleOutOfStockException(
      OutOfStockException ex, WebRequest request) {
    log.warn("Hết hàng: {}. Request: {}", ex.getMessage(), request.getDescription(false));
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
      ResourceNotFoundException ex, WebRequest request) {
    log.warn(
        "Tài nguyên không tìm thấy: {}. cho Request: {}",
        ex.getMessage(),
        request.getDescription(false));
    // Tạo ApiResponse
    ApiResponse<Object> apiResponse =
        ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
    return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleEntityNotFound(
      EntityNotFoundException ex, WebRequest request) {
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
      BadCredentialsException ex, WebRequest request) {
    String message = "Email hoặc mật khẩu không chính xác.";
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
      AccessDeniedException ex, WebRequest request) {
    String message = "Bạn không có quyền thực hiện hành động này.";
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
      DataIntegrityViolationException ex, WebRequest request) {
    String specificCauseMessage = ex.getMostSpecificCause().getMessage();
    String message = "Thao tác không thành công. Dữ liệu có thể đã bị trùng lặp";
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

  @ExceptionHandler(
      StorageFileNotFoundException.class) // Handler cụ thể cho StorageFileNotFoundException
  public ResponseEntity<ApiResponse<Object>> handleStorageFileNotFound(
      StorageFileNotFoundException ex, WebRequest request) {
    log.warn(
        "Storage File Not Found: {} for request: {}",
        ex.getMessage(),
        request.getDescription(false));
    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.NOT_FOUND.value(), // Trả về 404
            ex.getMessage(), // Message từ exception
            null // Không có details cụ thể
            );
    return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGlobalException(
      Exception ex, WebRequest request) {
    // 1. Log lại toàn bộ lỗi chi tiết để  debug
    log.error(
        "Unexpected internal server error processing request: {}",
        request.getDescription(false),
        ex);

    // 2. Chuẩn bị một thông báo lỗi thân thiện với người dùng
    String userFriendlyMessage =
        "Đã có lỗi không mong muốn xảy ra từ hệ thống. Vui lòng thử lại sau hoặc liên hệ bộ phận hỗ trợ.";

    // 3. Tạo đối tượng ApiResponse với thông báo thân thiện đó

    ApiResponse<Object> apiResponse =
        ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            userFriendlyMessage,
            null // Không gửi chi tiết lỗi cho người dùng
            );

    // 4. Trả về ResponseEntity với mã lỗi 500
    return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Xử lý lỗi khi người dùng chưa có vai trò BUSINESS_BUYER. Trả về status 403 Forbidden và một mã
   * lỗi tùy chỉnh.
   */
  @ExceptionHandler(BusinessAccountRequiredException.class)
  public ResponseEntity<ApiResponse<Object>> handleBusinessAccountRequired(
      BusinessAccountRequiredException ex) {
    // Tạo một map để chứa mã lỗi tùy chỉnh
    Map<String, Object> details = new HashMap<>();
    details.put("errorCode", "BUSINESS_ACCOUNT_REQUIRED");

    // Tạo ApiResponse với thông báo lỗi từ exception và details
    ApiResponse<Object> apiResponse =
        ApiResponse.builder()
            .success(false)
            .message(ex.getMessage()) // Lấy message gốc từ exception
            .status(HttpStatus.FORBIDDEN.value())
            .details(details) // Thêm mã lỗi vào details
            .build();
    return new ResponseEntity<>(apiResponse, HttpStatus.FORBIDDEN);
  }

  /**
   * Xử lý lỗi khi người dùng là BUSINESS_BUYER nhưng chưa hoàn thiện hồ sơ. Trả về status 400 Bad
   * Request và một mã lỗi tùy chỉnh.
   */
  @ExceptionHandler(BusinessProfileRequiredException.class)
  public ResponseEntity<ApiResponse<Object>> handleBusinessProfileRequired(
      BusinessProfileRequiredException ex) {
    Map<String, Object> details = new HashMap<>();
    details.put("errorCode", "BUSINESS_PROFILE_REQUIRED");

    ApiResponse<Object> apiResponse =
        ApiResponse.builder()
            .success(false)
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .details(details)
            .build();
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Xử lý lỗi khi người dùng là FARMER nhưng chưa hoàn thiện hồ sơ. Trả về status 400 Bad Request
   * và một mã lỗi tùy chỉnh.
   */
  @ExceptionHandler(FarmerProfileRequiredException.class)
  public ResponseEntity<ApiResponse<Object>> handleFarmerProfileRequired(
      FarmerProfileRequiredException ex) {
    Map<String, Object> details = new HashMap<>();
    details.put("errorCode", "FARMER_PROFILE_REQUIRED");

    ApiResponse<Object> apiResponse =
        ApiResponse.builder()
            .success(false)
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .details(details)
            .build();
    return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
  }
}
