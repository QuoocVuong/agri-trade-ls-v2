package com.yourcompany.agritrade.notification.domain;

import com.yourcompany.agritrade.common.model.NotificationType; // Import Enum mới
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient; // Người nhận

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "sender_id")
    // private User sender; // Người gửi (tùy chọn)

    @Lob // Cho message dài
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) // Độ dài đủ cho tên Enum
    private NotificationType type = NotificationType.OTHER;

    @Column(nullable = false)
    private boolean isRead = false;

    private LocalDateTime readAt;

    @Column(length = 512)
    private String link; // URL liên kết

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructor tiện ích
    public Notification(User recipient, String message, NotificationType type, String link) {
        this.recipient = recipient;
        this.message = message;
        this.type = type;
        this.link = link;
    }
}