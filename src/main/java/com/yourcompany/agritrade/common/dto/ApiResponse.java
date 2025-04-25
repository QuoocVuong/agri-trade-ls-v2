package com.yourcompany.agritrade.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude; // Chỉ include các field không null
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus; // Import HttpStatus

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL) // Không trả về các field null trong JSON
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data; // Dữ liệu trả về (generic)
    private Integer status; // Mã trạng thái HTTP
    private LocalDateTime timestamp;
    // Có thể thêm trường mã lỗi tùy chỉnh nếu cần
    // private String errorCode;

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

    // Response lỗi chung
    public static <T> ApiResponse<T> error(String message, HttpStatus status) {
        return new ApiResponse<>(false, message, null, status);
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
}