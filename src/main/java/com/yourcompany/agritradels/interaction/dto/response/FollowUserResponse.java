package com.yourcompany.agritradels.interaction.dto.response;

import lombok.Data;
import java.time.LocalDateTime; // Import nếu cần hiển thị thời gian follow

// DTO đơn giản để trả về danh sách người đang follow hoặc được follow
@Data
public class FollowUserResponse {
    private Long userId; // ID của user được follow/đang follow
    private String fullName;
    private String avatarUrl;
    // Có thể thêm các thông tin khác nếu cần (ví dụ: farmName nếu là farmer)
    // private String farmName;
    // private LocalDateTime followedAt; // Thời gian bắt đầu follow
}