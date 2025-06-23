package com.yourcompany.agritrade.interaction.domain;

import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_follows")
@Getter
@Setter
@NoArgsConstructor
@IdClass(UserFollow.UserFollowId.class) // Sử dụng khóa chính phức hợp
public class UserFollow {

  @Id // Phần của khóa chính phức hợp
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "follower_id", nullable = false)
  private User follower; // Người đi theo dõi

  @Id // Phần của khóa chính phức hợp
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "following_id", nullable = false)
  private User following; // Người được theo dõi

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime followedAt;

  // Lớp IdClass cho khóa chính phức hợp
  @Getter
  @Setter
  @NoArgsConstructor
  public static class UserFollowId implements Serializable {
    private Long follower;
    private Long following;

    public UserFollowId(Long followerId, Long followingId) {
      this.follower = followerId;
      this.following = followingId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UserFollowId that = (UserFollowId) o;
      return Objects.equals(follower, that.follower) && Objects.equals(following, that.following);
    }

    @Override
    public int hashCode() {
      return Objects.hash(follower, following);
    }
  }
}
