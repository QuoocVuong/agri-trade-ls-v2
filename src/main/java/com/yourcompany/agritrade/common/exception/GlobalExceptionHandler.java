package com.yourcompany.agritrade.common.exception;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException; // Import thêm
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException; // Import thêm
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException; // Import thêm
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException; // Import thêm
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException; // Import thêm
import org.springframework.web.HttpRequestMethodNotSupportedException; // Import thêm
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException; // Import thêm
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException; // Import thêm
import org.springframework.web.servlet.NoHandlerFoundException; // Import thêm (thay cho NoResourceFoundException trực tiếp)
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler; // Kế thừa để xử lý nhiều lỗi Spring MVC chuẩn

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE) // Đảm bảo ưu tiên hơn các handler mặc định
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler { // Kế thừa để override các handler chuẩn

    // --- Xử lý các lỗi Validation (@Valid) ---
    // (Override từ ResponseEntityExceptionHandler để tùy chỉnh response body)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        String errorDetails = errors.entrySet().stream()
                .map(entry -> "'" + entry.getKey() + "': " + entry.getValue())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: [{}] for request: {}", errorDetails, request.getDescription(false));
        ApiResponse<Map<String, String>> apiResponse = ApiResponse.badRequest("Validation Failed. Details: " + errorDetails);
        apiResponse.setData(errors); // Vẫn giữ data chi tiết cho frontend xử lý từng field
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    // --- Xử lý lỗi Request Body không đọc được (JSON sai cú pháp) ---
    // (Override từ ResponseEntityExceptionHandler)
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Malformed JSON request: {} for request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.badRequest("Malformed JSON request. Please check the request body format.");
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    // --- Xử lý lỗi thiếu Request Parameter (@RequestParam required=true) ---
    // (Override từ ResponseEntityExceptionHandler)
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String error = ex.getParameterName() + " parameter is missing";
        log.warn("Missing request parameter: {} for request: {}", error, request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.badRequest(error);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    // --- Xử lý lỗi sai kiểu dữ liệu của Request Parameter hoặc Path Variable ---
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        String error = String.format("The parameter '%s' of value '%s' could not be converted to type '%s'",
                ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName());
        log.warn("Type mismatch: {} for request: {}", error, request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.badRequest(error);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }


    // --- Xử lý lỗi sai phương thức HTTP (GET, POST,...) ---
    // (Override từ ResponseEntityExceptionHandler)
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(ex.getMethod());
        builder.append(" method is not supported for this request. Supported methods are ");
        ex.getSupportedHttpMethods().forEach(t -> builder.append(t).append(" "));
        log.warn("Method not supported: {} for request: {}", builder.toString(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.error(builder.toString(), HttpStatus.METHOD_NOT_ALLOWED);
        return new ResponseEntity<>(apiResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // --- Xử lý lỗi sai kiểu Content-Type của request body ---
    // (Override từ ResponseEntityExceptionHandler)
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(ex.getContentType());
        builder.append(" media type is not supported. Supported media types are ");
        ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(" "));
        log.warn("Media type not supported: {} for request: {}", builder.toString(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.error(builder.toString(), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        return new ResponseEntity<>(apiResponse, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // --- Xử lý lỗi Không tìm thấy Handler (URL không khớp) ---
    // (Override từ ResponseEntityExceptionHandler)
    // **Quan trọng:** Cần cấu hình `spring.mvc.throw-exception-if-no-handler-found=true`
    // và `spring.web.resources.add-mappings=false` trong application.properties
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String error = "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
        log.warn("No handler found: {} ", error);
        ApiResponse<Void> apiResponse = ApiResponse.notFound(error);
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    // --- Xử lý lỗi Bad Request tùy chỉnh (do logic nghiệp vụ) ---
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(BadRequestException ex, WebRequest request) {
        log.warn("Bad Request (Custom): {} for request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.badRequest(ex.getMessage());
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    // --- Xử lý lỗi Resource Not Found tùy chỉnh (do logic nghiệp vụ) ---
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource Not Found (Custom): {} for request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.notFound(ex.getMessage());
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    // --- Xử lý lỗi Entity Not Found (thường từ JPA getReferenceById,...) ---
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex, WebRequest request) {
        // Message của EntityNotFoundException có thể chứa tên class, nên trả về thông báo chung chung hơn
        log.warn("Entity Not Found: {} for request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.notFound("The requested entity was not found.");
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }


    // --- Xử lý lỗi Xác thực thất bại (Sai username/password) ---
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        log.warn("Authentication Failed: Invalid credentials for request: {}", request.getDescription(false));
        // Không nên ghi rõ là sai username hay password
        ApiResponse<Void> apiResponse = ApiResponse.unauthorized("Invalid username or password.");
        return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
    }


    // --- Xử lý lỗi Từ chối truy cập (Phân quyền - @PreAuthorize,...) ---
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Access Denied: {} for request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Void> apiResponse = ApiResponse.forbidden("You do not have permission to access this resource.");
        return new ResponseEntity<>(apiResponse, HttpStatus.FORBIDDEN);
    }

    // --- Xử lý lỗi Vi phạm ràng buộc dữ liệu (Unique, Foreign Key,...) ---
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        // Log lỗi gốc để debug nhưng không trả về chi tiết cho client
        log.error("Data Integrity Violation: {} for request: {}", ex.getMostSpecificCause().getMessage(), request.getDescription(false), ex);
        // Trả về lỗi 409 Conflict hoặc 400 Bad Request tùy ngữ cảnh
        // 409 thường phù hợp hơn cho unique constraint
        ApiResponse<Void> apiResponse = ApiResponse.error("Data integrity violation. There might be conflicting data (e.g., duplicate entry).", HttpStatus.CONFLICT);
        return new ResponseEntity<>(apiResponse, HttpStatus.CONFLICT);
    }


    // --- Xử lý tất cả các lỗi còn lại (Fallback) ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex, WebRequest request) {
        // Log lỗi nghiêm trọng với stack trace
        log.error("Unexpected error processing request: {}", request.getDescription(false), ex);
        ApiResponse<Void> apiResponse = ApiResponse.internalError("An unexpected internal server error occurred. Please try again later.");
        return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}