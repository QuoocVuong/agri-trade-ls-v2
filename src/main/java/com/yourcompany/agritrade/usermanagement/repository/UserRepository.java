package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  Boolean existsByEmail(String email);

  Boolean existsByPhoneNumber(String phoneNumber);

  // Nếu cần tìm user bất kể trạng thái isDeleted (ví dụ: kiểm tra email tồn tại tuyệt đối)
  @Query(
      "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email")
  boolean existsByEmailIgnoringSoftDelete(@Param("email") String email);

  @Query(
      "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phoneNumber = :phoneNumber")
  boolean existsByPhoneNumberIgnoringSoftDelete(@Param("phoneNumber") String phoneNumber);

  // Tìm user active (cả is_active=true và is_deleted=false)
  Optional<User> findByEmailAndIsActiveTrue(String email); // @Where đã xử lý is_deleted

  // Tìm user theo ID bất kể trạng thái isDeleted (ví dụ: cho admin khôi phục)
  @Query("SELECT u FROM User u WHERE u.id = :id")
  Optional<User> findByIdIncludingDeleted(@Param("id") Long id);

  // Thêm phương thức tìm user bằng verification token (bất kể active/deleted)
  @Query("SELECT u FROM User u WHERE u.verificationToken = :token")
  Optional<User> findByVerificationToken(@Param("token") String token);

  // Đếm user theo vai trò (cho Admin)
  @Query(
      "SELECT COUNT(u.id) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.isDeleted = false")
  long countByRoleName(@Param("roleName") RoleType roleName);

  // Lấy các user mới nhất
  List<User> findTopNByOrderByCreatedAtDesc(Pageable pageable);

  boolean existsByIdAndRoles_Name(Long id, RoleType roleName);

  List<User> findByRoles_Name(RoleType roleName);

  // Trong UserRepository.java
  @Query(
      "SELECT FUNCTION('DATE', u.createdAt) as registrationDate, COUNT(u.id) as userCount "
          + "FROM User u "
          + "WHERE u.createdAt BETWEEN :start AND :end "
          + "GROUP BY registrationDate "
          + "ORDER BY registrationDate ASC")
  List<Object[]> findDailyUserRegistrations(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /**
   * Tìm các nông dân có doanh thu cao nhất từ các đơn hàng đã hoàn thành (DELIVERED) trong một
   * khoảng thời gian nhất định.
   *
   * @param roleName Vai trò cần tìm (ROLE_FARMER).
   * @param orderStatus Trạng thái đơn hàng để tính doanh thu (DELIVERED).
   * @param startDate Ngày bắt đầu khoảng thời gian tính toán.
   * @param pageable Đối tượng Pageable để giới hạn số lượng kết quả (limit).
   * @return Danh sách các User là nông dân có doanh thu cao nhất.
   */
  @Query(
      "SELECT o.farmer FROM Order o JOIN o.farmer.roles r "
          + "WHERE r.name = :roleName "
          + "AND o.status = :orderStatus "
          + "AND o.createdAt >= :startDate "
          + "GROUP BY o.farmer "
          + "ORDER BY SUM(o.totalAmount) DESC")
  List<User> findTopFarmersByRevenue(
      @Param("roleName") RoleType roleName,
      @Param("orderStatus") com.yourcompany.agritrade.ordering.domain.OrderStatus orderStatus,
      @Param("startDate") LocalDateTime startDate,
      Pageable pageable);
}
