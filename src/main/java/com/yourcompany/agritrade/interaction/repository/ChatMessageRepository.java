package com.yourcompany.agritrade.interaction.repository;

import com.yourcompany.agritrade.interaction.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Tìm tin nhắn trong một phòng chat, phân trang, sắp xếp theo thời gian gửi giảm dần.
     * @param roomId ID của phòng chat.
     * @param pageable Thông tin phân trang và sắp xếp.
     * @return Trang tin nhắn.
     */
    Page<ChatMessage> findByRoomIdOrderBySentAtDesc(Long roomId, Pageable pageable);

    // Optional: Lấy tin nhắn mới nhất của một phòng
    Optional<ChatMessage> findTopByRoomIdOrderBySentAtDesc(Long roomId);

    /**
     * Đánh dấu tất cả tin nhắn chưa đọc của một người nhận trong một phòng chat là đã đọc.
     * @param roomId ID phòng chat.
     * @param recipientId ID người nhận.
     * @param readAt Thời gian đọc.
     * @return Số lượng tin nhắn đã được cập nhật.
     */
    @Modifying
    @Query("UPDATE ChatMessage msg SET msg.isRead = true, msg.readAt = :readAt " +
            "WHERE msg.room.id = :roomId AND msg.recipient.id = :recipientId AND msg.isRead = false")
    int markMessagesAsRead(@Param("roomId") Long roomId, @Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

    /**
     * Đếm số tin nhắn chưa đọc của một người nhận trong một phòng chat.
     * @param roomId ID phòng chat.
     * @param recipientId ID người nhận.
     * @return Số lượng tin nhắn chưa đọc.
     */
    long countByRoomIdAndRecipientIdAndIsReadFalse(Long roomId, Long recipientId);

}