package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.interaction.domain.FavoriteProduct;
import com.yourcompany.agritrade.interaction.repository.FavoriteProductRepository;
import com.yourcompany.agritrade.interaction.service.FavoriteService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteServiceImpl implements FavoriteService {

  private final FavoriteProductRepository favoriteProductRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final ProductMapper productMapper;

  @Override
  @Transactional
  public void addFavorite(Authentication authentication, Long productId) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Product product =
        productRepository
            .findById(productId) // Chỉ cho yêu thích sp chưa bị xóa
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

    if (favoriteProductRepository.existsByUserIdAndProductId(user.getId(), productId)) {
      log.warn("User {} already favorited product {}", user.getId(), productId);
      // throw new BadRequestException("Product already in favorites.");
      return; // Không làm gì nếu đã yêu thích
    }

    FavoriteProduct favorite = new FavoriteProduct();
    favorite.setUser(user);
    favorite.setProduct(product);
    favoriteProductRepository.save(favorite);

    updateFavoriteCount(productId, true); // Gọi hàm cập nhật count

    log.info("User {} added product {} to favorites", user.getId(), productId);
  }

  @Override
  @Transactional
  public void removeFavorite(Authentication authentication, Long productId) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();

    if (!favoriteProductRepository.existsByUserIdAndProductId(user.getId(), productId)) {
      log.warn("User {} has not favorited product {}", user.getId(), productId);
      // throw new BadRequestException("Product not in favorites.");
      return; // Không làm gì nếu chưa yêu thích
    }

    favoriteProductRepository.deleteByUserIdAndProductId(user.getId(), productId);

    updateFavoriteCount(productId, false); // Gọi hàm cập nhật count

    log.info("User {} removed product {} from favorites", user.getId(), productId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProductSummaryResponse> getMyFavorites(
      Authentication authentication, Pageable pageable) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    // Dùng phương thức repo để lấy Page<Product>
    Page<Product> favoriteProductsPage =
        favoriteProductRepository.findFavoriteProductsByUserId(user.getId(), pageable);
    // Map sang Page<ProductSummaryResponse>
    return favoriteProductsPage.map(productMapper::toProductSummaryResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isFavorite(Authentication authentication, Long productId) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    return favoriteProductRepository.existsByUserIdAndProductId(user.getId(), productId);
  }

  private void updateFavoriteCount(Long productId, boolean increment) {
    // Dùng cách đếm lại cho an toàn
    long currentCount = favoriteProductRepository.countByProductId(productId);
    productRepository
        .findById(productId)
        .ifPresent(
            product -> {
              product.setFavoriteCount((int) currentCount); // Cập nhật bằng số đếm mới nhất
              productRepository.save(product);
              log.debug("Updated favorite count for product {}: {}", productId, currentCount);
            });
  }
}
