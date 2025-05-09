package com.yourcompany.agritrade.usermanagement.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO đại diện cho thông tin của một Quyền hạn (Permission) trả về cho client.
 */
@Data // Lombok annotation để tự tạo getters, setters, equals, hashCode, toString
@NoArgsConstructor // Lombok annotation để tự tạo constructor không tham số
@AllArgsConstructor // Lombok annotation để tự tạo constructor với tất cả tham số
public class PermissionResponse {

    /**
     * ID của quyền hạn.
     */
    private Integer id;

    /**
     * Tên định danh duy nhất của quyền hạn (ví dụ: "USER_READ_ALL", "PRODUCT_CREATE").
     * Thường dùng để kiểm tra quyền trong code hoặc hiển thị cho admin.
     */
    private String name;

    /**
     * Mô tả chi tiết về mục đích hoặc ý nghĩa của quyền hạn này (có thể null).
     * Hữu ích để hiển thị cho admin hiểu rõ hơn về quyền.
     */
    private String description;
}