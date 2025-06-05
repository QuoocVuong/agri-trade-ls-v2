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

}
