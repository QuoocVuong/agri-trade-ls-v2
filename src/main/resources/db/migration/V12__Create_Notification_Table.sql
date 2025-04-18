-- V12__Create_Notification_Table.sql

-- Bảng Notifications (Lưu trữ thông báo trong ứng dụng)
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL COMMENT 'FK users.id (Người nhận)',
    -- sender_id BIGINT NULL COMMENT 'FK users.id (Người gửi/gây ra sự kiện - tùy chọn)',
    message TEXT NOT NULL COMMENT 'Nội dung thông báo',
    type ENUM('ORDER_PLACED', 'ORDER_STATUS_UPDATE', 'ORDER_CANCELLED', -- Order related
              'PAYMENT_SUCCESS', 'PAYMENT_FAILURE',                     -- Payment related
              'NEW_MESSAGE',                                           -- Chat related
              'NEW_FOLLOWER',                                          -- Follow related
              'PRODUCT_APPROVED', 'PRODUCT_REJECTED',                   -- Product related
              'REVIEW_APPROVED', 'REVIEW_REJECTED',                     -- Review related
              'WELCOME', 'PASSWORD_RESET', 'EMAIL_VERIFIED',           -- User related
              'SYSTEM_ANNOUNCEMENT', 'PROMOTION', 'OTHER') NOT NULL DEFAULT 'OTHER',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    link VARCHAR(512) NULL COMMENT 'URL liên kết đến nội dung chi tiết (đơn hàng, sản phẩm...)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
    -- FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_recipient_read ON notifications(recipient_id, is_read, created_at);
CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at);