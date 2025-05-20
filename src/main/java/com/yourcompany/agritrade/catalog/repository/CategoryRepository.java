package com.yourcompany.agritrade.catalog.repository;

import com.yourcompany.agritrade.catalog.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
  Optional<Category> findBySlug(String slug);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Integer id); // Kiểm tra slug tồn tại cho category khác

  // Lấy các category gốc (không có cha)
  List<Category> findByParentIsNull();

  // Lấy các category con trực tiếp
  List<Category> findByParentId(Integer parentId);

  // Lấy tất cả category con (đệ quy - có thể không hiệu quả, cân nhắc query khác)
  @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
  List<Category> findAllChildrenByParentId(Integer parentId);
}
