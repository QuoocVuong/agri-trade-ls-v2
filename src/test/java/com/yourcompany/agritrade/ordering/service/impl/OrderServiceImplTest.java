package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderItem;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse; // Import
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.OrderItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*; // Import static cho verify, when...

@ExtendWith(MockitoExtension.class) // Kích hoạt Mockito
class OrderServiceImplTest {

    @Mock // Tạo mock cho các dependency
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private NotificationService notificationService;
    // Không cần mock AddressRepository nếu không test checkout ở đây

    @InjectMocks // Inject các mock vào lớp cần test
    private OrderServiceImpl orderService;

    private User buyer;
    private User farmer;
    private Order order;
    private Product product;
    private OrderItem orderItem;
    private Authentication buyerAuth;

    @BeforeEach // Chạy trước mỗi test case để chuẩn bị dữ liệu chung
    void setUp() {
        // Tạo dữ liệu mẫu
        buyer = new User();
        buyer.setId(1L);
        buyer.setEmail("buyer@example.com");
        buyer.setFullName("Buyer Name");
        Role consumerRole = new Role(RoleType.ROLE_CONSUMER); // Giả sử Role có constructor này
        consumerRole.setId(1); // Giả sử ID
        buyer.setRoles(Collections.singleton(consumerRole));


        farmer = new User();
        farmer.setId(2L);
        farmer.setEmail("farmer@example.com");

        product = new Product();
        product.setId(10L);
        product.setName("Test Product");
        product.setStockQuantity(10);
        // product.setVersion(0L); // Nếu dùng optimistic lock

        orderItem = new OrderItem();
        orderItem.setId(100L);
        orderItem.setProduct(product);
        orderItem.setQuantity(2);
        orderItem.setPricePerUnit(new BigDecimal("50.00"));

        order = new Order();
        order.setId(1L);
        order.setOrderCode("ORD123");
        order.setBuyer(buyer);
        order.setFarmer(farmer);
        order.setStatus(OrderStatus.PENDING); // Trạng thái có thể hủy
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.addOrderItem(orderItem); // Thêm item vào order

        // Tạo đối tượng Authentication mẫu cho buyer
        buyerAuth = new UsernamePasswordAuthenticationToken(buyer.getEmail(), null,
                Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_CONSUMER.name())));
    }

    @Test
    @DisplayName("cancelOrder should successfully cancel a pending order for the buyer")
    void cancelOrder_Success_ForBuyer() {
        // Arrange
        // Giả lập hành vi của Repository
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(Collections.singletonList(orderItem));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Trả về chính order đã save
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Trả về product đã save
        // Giả lập findByIdWithDetails trả về order đã cập nhật (cho phần return cuối)
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        // Giả lập mapper trả về DTO (có thể không cần thiết nếu chỉ kiểm tra logic chính)
        when(orderMapper.toOrderResponse(any(Order.class))).thenReturn(new OrderResponse()); // Trả về DTO rỗng hoặc mock DTO

        // Act
        OrderResponse cancelledOrderResponse = orderService.cancelOrder(buyerAuth, order.getId());

        // Assert
        // 1. Kiểm tra trạng thái đơn hàng đã chuyển thành CANCELLED
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 2. Kiểm tra trạng thái thanh toán (FAILED vì đang PENDING)
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        // 3. Kiểm tra tồn kho sản phẩm đã được hoàn trả
        assertThat(product.getStockQuantity()).isEqualTo(10 + 2); // 10 ban đầu + 2 hoàn lại
        // 4. Kiểm tra các phương thức repository được gọi đúng số lần
        verify(userRepository, times(1)).findByEmail(buyer.getEmail());
        verify(orderRepository, times(1)).findById(order.getId());
        verify(orderItemRepository, times(1)).findByOrderId(order.getId());
        verify(productRepository, times(1)).findById(product.getId());
        verify(productRepository, times(1)).saveAndFlush(product); // Kiểm tra save product
        verify(orderRepository, times(1)).save(order); // Kiểm tra save order
        // 5. Kiểm tra NotificationService được gọi
        verify(notificationService, times(1)).sendOrderCancellationNotification(order);
        // 6. Kiểm tra kết quả trả về (có thể kiểm tra chi tiết hơn nếu cần)
        assertThat(cancelledOrderResponse).isNotNull();
    }

    @Test
    @DisplayName("cancelOrder should throw AccessDeniedException if user is not buyer or admin")
    void cancelOrder_Fail_NotOwner() {
        // Arrange
        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setEmail("other@example.com");
        Authentication otherAuth = new UsernamePasswordAuthenticationToken(otherUser.getEmail(), null, Collections.emptyList());

        when(userRepository.findByEmail(otherUser.getEmail())).thenReturn(Optional.of(otherUser));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order)); // Vẫn tìm thấy order

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(otherAuth, order.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to cancel this order");

        // Đảm bảo không có thay đổi nào được lưu
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(notificationService, never()).sendOrderCancellationNotification(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder should throw BadRequestException if order status is not cancellable")
    void cancelOrder_Fail_InvalidStatus() {
        // Arrange
        order.setStatus(OrderStatus.SHIPPING); // Set trạng thái không thể hủy
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancelOrder(buyerAuth, order.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Order cannot be cancelled in its current status: SHIPPING");

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(notificationService, never()).sendOrderCancellationNotification(any(Order.class));
    }

    // Thêm các test case khác cho các trường hợp:
    // - Hủy bởi Admin
    // - Hủy đơn hàng đã thanh toán (kiểm tra paymentStatus -> REFUNDED)
    // - Sản phẩm không tìm thấy khi hoàn kho
    // - Lỗi Optimistic Lock khi hoàn kho (khó test trực tiếp trong unit test, cần integration test)
}