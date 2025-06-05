package com.yourcompany.agritrade.catalog.repository;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Kế thừa JpaSpecificationExecutor để dùng Specification API cho filter động
public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  Optional<Product> findBySlug(String slug);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);

  // Tìm sản phẩm theo ID và Farmer ID (kiểm tra ownership)
  Optional<Product> findByIdAndFarmerId(Long id, Long farmerId);

  // Tìm sản phẩm theo Slug và Farmer ID
  Optional<Product> findBySlugAndFarmerId(String slug, Long farmerId);

  // Lấy sản phẩm của một Farmer (phân trang)
  Page<Product> findByFarmerId(Long farmerId, Pageable pageable);

  // Lấy sản phẩm public theo slug
  Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

  // Lấy sản phẩm public theo ID
  Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

  // Đếm sản phẩm theo category (hữu ích khi xóa category)
  long countByCategoryId(Integer categoryId);




  /**
   * Tìm các sản phẩm khác trong cùng danh mục (đã published, không bao gồm sản phẩm hiện tại). Giới
   * hạn số lượng trả về.
   *
   * @param categoryId ID của danh mục.
   * @param currentProductId ID của sản phẩm đang xem (để loại trừ).
   * @param status Trạng thái mong muốn (PUBLISHED).
   * @param pageable Đối tượng Pageable để giới hạn số lượng (ví dụ: PageRequest.of(0, limit)).
   * @return Danh sách sản phẩm liên quan cùng danh mục.
   */
  List<Product> findTopNByCategoryIdAndIdNotAndStatus(
      Integer categoryId, Long currentProductId, ProductStatus status, Pageable pageable);

  /**
   * Tìm các sản phẩm khác của cùng nông dân (đã published, không bao gồm sản phẩm hiện tại và các
   * sản phẩm đã lấy ở bước trước). Giới hạn số lượng trả về.
   *
   * @param farmerId ID của nông dân.
   * @param excludedProductIds Danh sách ID các sản phẩm cần loại trừ (sản phẩm hiện tại và sp cùng
   *     category đã lấy).
   * @param status Trạng thái mong muốn (PUBLISHED).
   * @param pageable Đối tượng Pageable để giới hạn số lượng.
   * @return Danh sách sản phẩm liên quan cùng nông dân.
   */
  // Sử dụng @Query vì tên phương thức sẽ quá dài và phức tạp với nhiều điều kiện NOT IN
  @Query(
      "SELECT p FROM Product p "
          + "WHERE p.farmer.id = :farmerId "
          + "AND p.status = :status "
          + "AND p.id NOT IN :excludedProductIds") // Loại trừ các ID đã có
  List<Product> findTopNByFarmerIdAndIdNotInAndStatus(
      @Param("farmerId") Long farmerId,
      @Param("excludedProductIds") List<Long> excludedProductIds, // Truyền list ID cần loại trừ
      @Param("status") ProductStatus status,
      Pageable pageable);

  @Query(
      "SELECT p FROM Product p "
          + "WHERE p.status = :published "
          + "AND p.id <> :productId "
          + "AND (p.category.id = :categoryId OR p.farmer.id = :farmerId)")
  List<Product> findRelatedProducts(
      @Param("productId") Long productId,
      @Param("categoryId") Long categoryId,
      @Param("farmerId") Long farmerId,
      @Param("published") ProductStatus published,
      Pageable pageable);

  // Phương thức tương tự nhưng không cần loại trừ ID (dùng nếu chỉ lấy theo farmer)
  List<Product> findTopNByFarmerIdAndIdNotAndStatus(
      Long farmerId, Long currentProductId, ProductStatus status, Pageable pageable);

  // Đếm sản phẩm sắp hết hàng của Farmer (ví dụ: < 5)
  Long countByFarmerIdAndStockQuantityLessThan(Long farmerId, int threshold);


  @Query(
      "SELECT new com.yourcompany.agritrade.catalog.dto.response.TopProductResponse("
          + "oi.product.id, oi.product.name, oi.product.slug, SUM(oi.quantity), SUM(oi.totalPrice)) "
          + // Bỏ thumbnailUrl
          "FROM OrderItem oi JOIN oi.product p "
          + // Thêm JOIN tường minh để có thể group by product
          "WHERE oi.order.farmer.id = :farmerId AND oi.order.status = com.yourcompany.agritrade.ordering.domain.OrderStatus.DELIVERED "
          + "GROUP BY p.id, p.name, p.slug "
          + // Group by các trường của product
          "ORDER BY SUM(oi.quantity) DESC")
  List<TopProductResponse> findTopSellingProductsByFarmerWithoutThumbnail(
      @Param("farmerId") Long farmerId, Pageable pageable);

  // Đếm sản phẩm theo trạng thái (cho Admin)
  Long countByStatus(ProductStatus status);


  Page<Product> findByFarmerIdAndStatus(Long farmerId, ProductStatus status, Pageable pageable);

  @Query(
      "SELECT p FROM Product p "
          + "LEFT JOIN FETCH p.farmer f "
          + "LEFT JOIN FETCH f.farmerProfile fp "
          + "LEFT JOIN FETCH p.category c "
          + "LEFT JOIN FETCH p.images img "
          + // Fetch images
          "LEFT JOIN FETCH p.pricingTiers pt "
          + // Fetch pricing tiers
          "WHERE p.id = :productId")
  Optional<Product> findByIdWithDetails(@Param("productId") Long productId);



}
