package com.yourcompany.agritradels.interaction.repository;

import com.yourcompany.agritradels.interaction.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * Tìm phòng chat giữa hai người dùng (bất kể thứ tự user1, user2).
     * @param userId1 ID người dùng thứ nhất.
     * @param userId2 ID người dùng thứ hai.
     * @return Optional chứa ChatRoom nếu tìm thấy.
     */
    @Query("SELECT cr FROM ChatRoom cr WHERE (cr.user1.id = :userId1 AND cr.user2.id = :userId2) OR (cr.user1.id = :userId2 AND cr.user2.id = :userId1)")
    Optional<ChatRoom> findRoomBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * Tìm tất cả các phòng chat mà một người dùng tham gia.
     * Sắp xếp theo thời gian tin nhắn cuối cùng giảm dần để hiển thị gần đây nhất lên đầu.
     * Sử dụng JOIN FETCH để tải thông tin user và tin nhắn cuối cùng (nếu cần).
     * @param userId ID của người dùng.
     * @return Danh sách các phòng chat.
     */
    @Query("SELECT cr FROM ChatRoom cr " +
            "LEFT JOIN FETCH cr.user1 u1 " + // Fetch thông tin user1
            "LEFT JOIN FETCH cr.user2 u2 " + // Fetch thông tin user2
            "LEFT JOIN FETCH cr.lastMessage lm " + // Fetch tin nhắn cuối
            "LEFT JOIN FETCH lm.sender " + // Fetch người gửi tin nhắn cuối
            "WHERE cr.user1.id = :userId OR cr.user2.id = :userId " +
            "ORDER BY cr.lastMessageTime DESC NULLS LAST") // Ưu tiên phòng có tin nhắn mới, phòng chưa có tin nhắn xuống cuối
    List<ChatRoom> findRoomsByUser(@Param("userId") Long userId);

    // Có thể thêm các phương thức khác như đếm tổng số tin nhắn chưa đọc của user
    @Query("SELECT SUM(CASE WHEN cr.user1.id = :userId THEN cr.user1UnreadCount ELSE cr.user2UnreadCount END) " +
            "FROM ChatRoom cr WHERE cr.user1.id = :userId OR cr.user2.id = :userId")
    Optional<Integer> getTotalUnreadCountForUser(@Param("userId") Long userId);
}