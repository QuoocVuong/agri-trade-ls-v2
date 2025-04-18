package com.yourcompany.agritradels.interaction.repository;

import com.yourcompany.agritradels.interaction.domain.UserFollow;
import com.yourcompany.agritradels.usermanagement.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Khóa chính là UserFollowId (lớp lồng) nhưng JpaRepository vẫn dùng kiểu của Entity và ID class
public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.UserFollowId> {

    // Kiểm tra xem user A có đang follow user B không
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // Tìm bản ghi follow cụ thể
    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // Lấy danh sách những người mà user này đang follow (following)
    @Query("SELECT uf.following FROM UserFollow uf WHERE uf.follower.id = :followerId")
    List<User> findFollowingByFollowerId(@Param("followerId") Long followerId);
    // Có thể trả về Page<User> nếu cần phân trang

    // Lấy danh sách những người đang follow user này (followers)
    @Query("SELECT uf.follower FROM UserFollow uf WHERE uf.following.id = :followingId")
    List<User> findFollowersByFollowingId(@Param("followingId") Long followingId);
    // Có thể trả về Page<User> nếu cần phân trang

    // Đếm số người đang follow
    long countByFollowerId(Long followerId);

    // Đếm số người được follow
    long countByFollowingId(Long followingId);

    // Xóa bản ghi follow
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);
}