package com.yourcompany.agritrade.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL) // Không trả về các field null trong JSON
@Slf4j
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private String message;
  private T data; // Dữ liệu trả về (generic)
  private Integer status; // Mã trạng thái HTTP
  private LocalDateTime timestamp;

  // Constructor private để khuyến khích dùng static factory methods
  private ApiResponse(boolean success, String message, T data, HttpStatus status) {
    this.success = success;
    this.message = message;
    this.data = data;
    this.status = status.value(); // Lấy giá trị số của HttpStatus
    this.timestamp = LocalDateTime.now();
  }

  // --- Static Factory Methods ---

  // Response thành công với dữ liệu
  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, message, data, HttpStatus.OK);
  }

  public static <T> ApiResponse<T> success(T data) {
    return success(data, "Operation successful");
  }

  // Response thành công không có dữ liệu (ví dụ: DELETE)
  public static <Void> ApiResponse<Void> success(String message) {
    return new ApiResponse<>(true, message, null, HttpStatus.OK);
  }

  // Response tạo mới thành công (201 Created)
  public static <T> ApiResponse<T> created(T data, String message) {
    return new ApiResponse<>(true, message, data, HttpStatus.CREATED);
  }

  public static <T> ApiResponse<T> created(T data) {
    return created(data, "Resource created successfully");
  }

  // Response lỗi cụ thể hơn (ví dụ: Bad Request)
  public static <T> ApiResponse<T> badRequest(String message) {
    return error(message, HttpStatus.BAD_REQUEST);
  }

  // Response lỗi Not Found
  public static <T> ApiResponse<T> notFound(String message) {
    return error(message, HttpStatus.NOT_FOUND);
  }

  // Response lỗi Unauthorized
  public static <T> ApiResponse<T> unauthorized(String message) {
    return error(message, HttpStatus.UNAUTHORIZED);
  }

  // Response lỗi Forbidden
  public static <T> ApiResponse<T> forbidden(String message) {
    return error(message, HttpStatus.FORBIDDEN);
  }

  // Response lỗi Server Internal Error
  public static <T> ApiResponse<T> internalError(String message) {
    return error(message, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private Map<String, Object> details; // Thêm trường details

  // Constructor mới
  private ApiResponse(
      boolean success, String message, T data, HttpStatus status, Map<String, Object> details) {
    this.success = success;
    this.message = message;
    this.data = data;
    this.status = status.value();
    this.timestamp = LocalDateTime.now();
    this.details = details; // Gán details
  }

  // Factory method mới cho lỗi với details
  public static <T> ApiResponse<T> error(int statusCode, String message, Object details) {
    HttpStatus status = HttpStatus.resolve(statusCode);
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR; // Default nếu status code không hợp lệ
    }
    Map<String, Object> detailMap = null;
    if (details != null) {
      if (details instanceof Map) {
        // Ép kiểu an toàn nếu là Map
        try {
          @SuppressWarnings("unchecked")
          Map<String, Object> castedDetails = (Map<String, Object>) details;
          detailMap = castedDetails;
        } catch (ClassCastException e) {
          log.warn("Could not cast details to Map<String, Object>. Storing as single entry.");
          detailMap = new HashMap<>();
          detailMap.put("errorInfo", details);
        }

      } else {
        // Nếu không phải Map, tạo map mới chứa nó
        detailMap = new HashMap<>();
        detailMap.put("errorInfo", details);
      }
    }
    return new ApiResponse<>(false, message, null, status, detailMap);
  }

  // Overload factory method cũ để gọi factory method mới với details là null
  public static <T> ApiResponse<T> error(String message, HttpStatus status) {
    return error(status.value(), message, null);
  }

  public static <T> ApiResponse<T> error(int statusCode, String message) {
    return error(statusCode, message, null);
  }
}
