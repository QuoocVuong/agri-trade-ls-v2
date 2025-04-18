package com.yourcompany.agritradels.usermanagement.repository;

import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.usermanagement.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    Boolean existsByPhoneNumber(String phoneNumber);
    //Optional<User> findByEmailAndIsActiveTrue(String email);


    // Nếu cần tìm user bất kể trạng thái isDeleted (ví dụ: kiểm tra email tồn tại tuyệt đối)
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email")
    boolean existsByEmailIgnoringSoftDelete(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phoneNumber = :phoneNumber")
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
    @Query("SELECT COUNT(u.id) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.isDeleted = false")
    long countByRoleName(@Param("roleName") RoleType roleName);


    // Lấy các user mới nhất
    List<User> findTopNByOrderByCreatedAtDesc(Pageable pageable);

}