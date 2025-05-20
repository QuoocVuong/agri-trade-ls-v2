package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.usermanagement.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

  // Tìm tất cả địa chỉ của một user (chưa bị xóa mềm)
  // @Where trên Address entity sẽ tự lọc is_deleted = false
  List<Address> findByUserId(Long userId);

  // Tìm địa chỉ theo ID và User ID (kiểm tra ownership và chưa bị xóa mềm)
  Optional<Address> findByIdAndUserId(Long id, Long userId);

  // Tìm địa chỉ mặc định của user
  Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
