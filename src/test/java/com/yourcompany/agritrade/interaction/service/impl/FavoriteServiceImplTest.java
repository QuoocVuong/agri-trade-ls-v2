package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.interaction.domain.FavoriteProduct;
import com.yourcompany.agritrade.interaction.repository.FavoriteProductRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock private FavoriteProductRepository favoriteProductRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private Authentication authentication;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    private User testUser;
    private Product product1, product2;
    private FavoriteProduct favoriteProduct1;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");
        testUser.setFullName("Test User");

        product1 = new Product();
        product1.setId(10L);
        product1.setName("Sản phẩm Yêu thích 1");
        product1.setFavoriteCount(0);

        product2 = new Product();
        product2.setId(20L);
        product2.setName("Sản phẩm Yêu thích 2");
        product2.setFavoriteCount(5);


        favoriteProduct1 = new FavoriteProduct();
        favoriteProduct1.setUser(testUser);
        favoriteProduct1.setProduct(product1);

        lenient().when(authentication.getName()).thenReturn(testUser.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
    }

    @Nested
    @DisplayName("Add Favorite Tests")
    class AddFavoriteTests {
        @Test
        @DisplayName("Add Favorite - New Product - Success")
        void addFavorite_newProduct_shouldSaveFavoriteAndUpdateCount() {
            when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(false);
            when(favoriteProductRepository.save(any(FavoriteProduct.class))).thenReturn(favoriteProduct1);
            when(favoriteProductRepository.countByProductId(product1.getId())).thenReturn(1L); // Sau khi thêm, count là 1
            when(productRepository.save(any(Product.class))).thenReturn(product1);

            favoriteService.addFavorite(authentication, product1.getId());

            ArgumentCaptor<FavoriteProduct> favoriteCaptor = ArgumentCaptor.forClass(FavoriteProduct.class);
            verify(favoriteProductRepository).save(favoriteCaptor.capture());
            assertEquals(testUser, favoriteCaptor.getValue().getUser());
            assertEquals(product1, favoriteCaptor.getValue().getProduct());

            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertEquals(1, productCaptor.getValue().getFavoriteCount());
        }

        @Test
        @DisplayName("Add Favorite - Product Already Favorited - Should Do Nothing")
        void addFavorite_productAlreadyFavorited_shouldDoNothing() {
            when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(true);

            favoriteService.addFavorite(authentication, product1.getId());

            verify(favoriteProductRepository, never()).save(any(FavoriteProduct.class));
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("Add Favorite - Product Not Found - Throws ResourceNotFoundException")
        void addFavorite_productNotFound_shouldThrowResourceNotFoundException() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> favoriteService.addFavorite(authentication, 99L));
        }
    }

    @Nested
    @DisplayName("Remove Favorite Tests")
    class RemoveFavoriteTests {
        @Test
        @DisplayName("Remove Favorite - Product Is Favorited - Success")
        void removeFavorite_productIsFavorited_shouldDeleteFavoriteAndUpdateCount() {
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(true);
            doNothing().when(favoriteProductRepository).deleteByUserIdAndProductId(testUser.getId(), product1.getId());
            when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
            when(favoriteProductRepository.countByProductId(product1.getId())).thenReturn(0L); // Sau khi xóa, count là 0
            when(productRepository.save(any(Product.class))).thenReturn(product1);

            favoriteService.removeFavorite(authentication, product1.getId());

            verify(favoriteProductRepository).deleteByUserIdAndProductId(testUser.getId(), product1.getId());
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertEquals(0, productCaptor.getValue().getFavoriteCount());
        }

        @Test
        @DisplayName("Remove Favorite - Product Not Favorited - Should Do Nothing")
        void removeFavorite_productNotFavorited_shouldDoNothing() {
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(false);

            favoriteService.removeFavorite(authentication, product1.getId());

            verify(favoriteProductRepository, never()).deleteByUserIdAndProductId(anyLong(), anyLong());
            verify(productRepository, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("Get My Favorites Tests")
    class GetMyFavoritesTests {
        @Test
        @DisplayName("Get My Favorites - Success")
        void getMyFavorites_shouldReturnPageOfProductSummaries() {
            Pageable pageable = PageRequest.of(0, 10);
            List<Product> favoriteProductsList = List.of(product1, product2);
            Page<Product> favoriteProductsPage = new PageImpl<>(favoriteProductsList, pageable, favoriteProductsList.size());

            ProductSummaryResponse summary1 = new ProductSummaryResponse(); summary1.setId(product1.getId());
            ProductSummaryResponse summary2 = new ProductSummaryResponse(); summary2.setId(product2.getId());

            when(favoriteProductRepository.findFavoriteProductsByUserId(testUser.getId(), pageable))
                    .thenReturn(favoriteProductsPage);
            when(productMapper.toProductSummaryResponse(product1)).thenReturn(summary1);
            when(productMapper.toProductSummaryResponse(product2)).thenReturn(summary2);

            Page<ProductSummaryResponse> result = favoriteService.getMyFavorites(authentication, pageable);

            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
            assertEquals(summary1.getId(), result.getContent().get(0).getId());
            assertEquals(summary2.getId(), result.getContent().get(1).getId());
        }

        @Test
        @DisplayName("Get My Favorites - No Favorites")
        void getMyFavorites_whenNoFavorites_shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(favoriteProductRepository.findFavoriteProductsByUserId(testUser.getId(), pageable))
                    .thenReturn(emptyPage);

            Page<ProductSummaryResponse> result = favoriteService.getMyFavorites(authentication, pageable);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Is Favorite Tests")
    class IsFavoriteTests {
        @Test
        @DisplayName("Is Favorite - Product Is Favorited - Returns True")
        void isFavorite_whenProductIsFavorited_shouldReturnTrue() {
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(true);
            boolean result = favoriteService.isFavorite(authentication, product1.getId());
            assertTrue(result);
        }

        @Test
        @DisplayName("Is Favorite - Product Not Favorited - Returns False")
        void isFavorite_whenProductNotFavorited_shouldReturnFalse() {
            when(favoriteProductRepository.existsByUserIdAndProductId(testUser.getId(), product2.getId())).thenReturn(false);
            boolean result = favoriteService.isFavorite(authentication, product2.getId());
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Authentication Helper Tests")
    class AuthenticationHelperTests {
        @Test
        @DisplayName("Get User From Authentication - User Not Found - Throws UsernameNotFoundException")
        void getUserFromAuthentication_whenUserNotFound_shouldThrowUsernameNotFoundException() {
            when(authentication.getName()).thenReturn("unknown@example.com");
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
            assertThrows(UsernameNotFoundException.class,
                    () -> favoriteService.addFavorite(authentication, product1.getId()));
        }

        @Test
        @DisplayName("Get User From Authentication - Not Authenticated - Throws AccessDeniedException")
        void getUserFromAuthentication_whenNotAuthenticated_shouldThrowAccessDeniedException() {
            when(authentication.isAuthenticated()).thenReturn(false);
            assertThrows(AccessDeniedException.class,
                    () -> favoriteService.addFavorite(authentication, product1.getId()));
        }
    }

    // XÓA BỎ HOÀN TOÀN UpdateFavoriteCountTests
    // @Nested
    // @DisplayName("Update Favorite Count Helper Tests")
    // class UpdateFavoriteCountTests {
    //     // ... (các test case cũ ở đây) ...
    // }
}