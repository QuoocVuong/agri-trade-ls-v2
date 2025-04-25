package com.yourcompany.agritrade.interaction.domain;

import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "chat_rooms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id_1", "user_id_2"}) // Đảm bảo cặp user là duy nhất
})
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id_1", nullable = false)
    private User user1; // User có ID nhỏ hơn

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id_2", nullable = false)
    private User user2; // User có ID lớn hơn

    @OneToOne(fetch = FetchType.LAZY) // Liên kết với tin nhắn cuối cùng
    @JoinColumn(name = "last_message_id")
    private ChatMessage lastMessage;

    private LocalDateTime lastMessageTime;

    @Column(nullable = false)
    private int user1UnreadCount = 0;

    @Column(nullable = false)
    private int user2UnreadCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructor để dễ tạo (đảm bảo user1.id < user2.id)
    public ChatRoom(User user1, User user2) {
        if (user1.getId() < user2.getId()) {
            this.user1 = user1;
            this.user2 = user2;
        } else if (user1.getId() > user2.getId()) {
            this.user1 = user2;
            this.user2 = user1;
        } else {
            // Không cho phép chat với chính mình
            throw new IllegalArgumentException("Cannot create chat room with the same user.");
        }
    }

    // equals và hashCode dựa trên cặp user
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRoom chatRoom = (ChatRoom) o;
        // So sánh ID của user1 và user2 bất kể thứ tự
        return (Objects.equals(user1.getId(), chatRoom.user1.getId()) && Objects.equals(user2.getId(), chatRoom.user2.getId())) ||
                (Objects.equals(user1.getId(), chatRoom.user2.getId()) && Objects.equals(user2.getId(), chatRoom.user1.getId()));
    }

    @Override
    public int hashCode() {
        // Hash dựa trên ID của user, đảm bảo thứ tự không ảnh hưởng
        long id1 = user1.getId();
        long id2 = user2.getId();
        return Objects.hash(Math.min(id1, id2), Math.max(id1, id2));
    }
}