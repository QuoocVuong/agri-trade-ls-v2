package com.yourcompany.agritrade.notification.repository;

import com.yourcompany.agritrade.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Tìm thông báo cho người dùng, sắp xếp theo thời gian giảm dần
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // Đếm số thông báo chưa đọc của người dùng
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // Đánh dấu tất cả thông báo của người dùng là đã đọc
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsReadForRecipient(@Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

    // Đánh dấu một thông báo cụ thể là đã đọc
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id = :notificationId AND n.recipient.id = :recipientId")
    int markAsRead(@Param("notificationId") Long notificationId, @Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

}