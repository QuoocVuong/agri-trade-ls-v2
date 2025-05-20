package com.yourcompany.agritrade.interaction.domain;

import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private ChatRoom room;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false)
  private User recipient;

  @Lob // Cho nội dung dài
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MessageType messageType = MessageType.TEXT; // Tạo Enum này

  @Column(nullable = false, updatable = false)
  private LocalDateTime sentAt = LocalDateTime.now(); // Gán thời gian khi tạo

  @Column(nullable = false)
  private boolean isRead = false;

  private LocalDateTime readAt;
}
