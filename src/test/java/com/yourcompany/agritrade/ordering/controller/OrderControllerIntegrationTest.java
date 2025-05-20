package com.yourcompany.agritrade.ordering.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.AbstractIntegrationTest;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.repository.PaymentRepository;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
// Không cần @SpringBootTest và @ActiveProfiles vì đã kế thừa từ AbstractIntegrationTest
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AddressRepository addressRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private CartItemRepository cartItemRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private User testBuyer;
  private User testFarmer;
  private Address testAddress;
  private Product testProduct;
  private Role buyerRole;
  private Role farmerRole;
  private Role adminRole;

  // Dùng @BeforeEach để tạo dữ liệu mới cho mỗi test
  // @Transactional sẽ rollback sau mỗi test
  @BeforeEach
  void setUpTestData() {
    // Tạo Roles
    buyerRole =
        roleRepository
            .findByName(RoleType.ROLE_CONSUMER)
            .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_CONSUMER)));
    farmerRole =
        roleRepository
            .findByName(RoleType.ROLE_FARMER)
            .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_FARMER)));
    adminRole =
        roleRepository
            .findByName(RoleType.ROLE_ADMIN)
            .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_ADMIN)));

    // Tạo Buyer
    testBuyer = new User();
    testBuyer.setEmail("buyer.test@example.com");
    testBuyer.setPasswordHash(passwordEncoder.encode("password"));
    testBuyer.setFullName("Test Buyer");
    testBuyer.setActive(true);
    testBuyer.setRoles(Collections.singleton(buyerRole));
    testBuyer = userRepository.saveAndFlush(testBuyer); // Flush để đảm bảo có ID

    // Tạo Farmer
    testFarmer = new User();
    testFarmer.setEmail("farmer.test@example.com");
    testFarmer.setPasswordHash(passwordEncoder.encode("password"));
    testFarmer.setFullName("Test Farmer");
    testFarmer.setActive(true);
    testFarmer.setRoles(Collections.singleton(farmerRole));
    testFarmer = userRepository.saveAndFlush(testFarmer);

    // Tạo Address cho Buyer
    testAddress = new Address();
    testAddress.setUser(testBuyer);
    testAddress.setFullName("Test Buyer Address");
    testAddress.setPhoneNumber("123456789");
    testAddress.setAddressDetail("123 Test St");
    testAddress.setProvinceCode("12");
    testAddress.setDistrictCode("121");
    testAddress.setWardCode("12101");
    testAddress.setDefault(true);
    testAddress = addressRepository.saveAndFlush(testAddress);

    // Tạo Category
    Category category = new Category();
    category.setName("Rau Test");
    category.setSlug("rau-test-" + UUID.randomUUID()); // Đảm bảo slug unique
    category = categoryRepository.saveAndFlush(category);

    // Tạo Product
    testProduct = new Product();
    testProduct.setName("Rau Muống Test");
    testProduct.setSlug("rau-muong-test-" + UUID.randomUUID()); // Đảm bảo slug unique
    testProduct.setFarmer(testFarmer);
    testProduct.setCategory(category);
    testProduct.setUnit("bó");
    testProduct.setPrice(new BigDecimal("10000.00"));
    testProduct.setStockQuantity(50);
    testProduct.setStatus(ProductStatus.PUBLISHED);
    testProduct.setProvinceCode("12");
    testProduct = productRepository.saveAndFlush(testProduct);

    // Thêm vào giỏ hàng của Buyer
    CartItem testCartItem = new CartItem();
    testCartItem.setUser(testBuyer);
    testCartItem.setProduct(testProduct);
    testCartItem.setQuantity(3);
    cartItemRepository.saveAndFlush(testCartItem);
  }

  // --- Test Cases cho OrderController ---

  @Test
  @DisplayName("[POST /api/orders/checkout] Thành công khi giỏ hàng hợp lệ")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void checkout_Success_WhenCartNotEmptyAndStockAvailable() throws Exception {
    CheckoutRequest checkoutRequest = new CheckoutRequest();
    checkoutRequest.setShippingAddressId(testAddress.getId());
    checkoutRequest.setPaymentMethod(PaymentMethod.COD);
    checkoutRequest.setNotes("Giao nhanh giúp");

    mockMvc
        .perform(
            post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checkoutRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].status", is(OrderStatus.PENDING.name())));

    assertThat(cartItemRepository.findByUserId(testBuyer.getId())).isEmpty();
    Product productAfter = productRepository.findById(testProduct.getId()).orElseThrow();
    assertThat(productAfter.getStockQuantity()).isEqualTo(50 - 3);
  }

  @Test
  @DisplayName("[POST /api/orders/checkout] Thất bại khi giỏ hàng trống")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void checkout_Fail_WhenCartIsEmpty() throws Exception {
    cartItemRepository.deleteAll(); // Xóa giỏ hàng
    CheckoutRequest checkoutRequest = new CheckoutRequest();
    checkoutRequest.setShippingAddressId(testAddress.getId());
    checkoutRequest.setPaymentMethod(PaymentMethod.COD);

    mockMvc
        .perform(
            post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checkoutRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.message", containsString("Cart is empty")));
  }

  @Test
  @DisplayName("[GET /api/orders/my] Lấy đúng danh sách đơn hàng của Buyer")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void getMyOrdersAsBuyer_Success() throws Exception {
    // Arrange: Tạo 2 đơn hàng cho buyer này
    createTestOrder(testBuyer, testFarmer, OrderStatus.PENDING, PaymentStatus.PENDING);
    createTestOrder(testBuyer, testFarmer, OrderStatus.DELIVERED, PaymentStatus.PAID);
    // Tạo 1 đơn hàng cho user khác (sẽ không hiển thị)
    User otherBuyer =
        userRepository.save(
            User.builder()
                .email("other@test.com")
                .passwordHash("...")
                .fullName("Other")
                .isActive(true)
                .roles(Collections.singleton(buyerRole))
                .build());
    createTestOrder(otherBuyer, testFarmer, OrderStatus.PENDING, PaymentStatus.PENDING);

    MvcResult result =
        mockMvc
            .perform(get("/api/orders/my").param("page", "0").param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(
                jsonPath("$.data.content", hasSize(2))) // Chỉ mong đợi 2 đơn hàng của testBuyer
            .andExpect(jsonPath("$.data.totalElements", is(2)))
            .andReturn();

    // Optional: Parse response để kiểm tra kỹ hơn
    // ApiResponse<Page<OrderSummaryResponse>> response = objectMapper.readValue(
    //     result.getResponse().getContentAsString(),
    //     new TypeReference<ApiResponse<Page<OrderSummaryResponse>>>() {});
    // assertThat(response.getData().getContent()).allMatch(o ->
    // o.getBuyerName().equals(testBuyer.getFullName()));
  }

  @Test
  @DisplayName("[GET /api/orders/{orderId}] Buyer lấy được chi tiết đơn hàng của mình")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void getMyOrderDetailsById_Success_BuyerIsOwner() throws Exception {
    Order order =
        createTestOrder(testBuyer, testFarmer, OrderStatus.PROCESSING, PaymentStatus.PENDING);

    mockMvc
        .perform(get("/api/orders/{orderId}", order.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.id", is(order.getId().intValue())))
        .andExpect(jsonPath("$.data.buyer.id", is(testBuyer.getId().intValue())));
  }

  @Test
  @DisplayName("[GET /api/orders/{orderId}] Buyer không lấy được chi tiết đơn hàng của người khác")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void getMyOrderDetailsById_Fail_BuyerIsNotOwner() throws Exception {
    User otherBuyer =
        userRepository.save(
            User.builder()
                .email("other2@test.com")
                .passwordHash("...")
                .fullName("Other 2")
                .isActive(true)
                .roles(Collections.singleton(buyerRole))
                .build());
    Order otherOrder =
        createTestOrder(otherBuyer, testFarmer, OrderStatus.PENDING, PaymentStatus.PENDING);

    mockMvc
        .perform(get("/api/orders/{orderId}", otherOrder.getId()))
        .andExpect(status().isForbidden()) // Mong đợi 403 Forbidden
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.message", containsString("User does not have permission")));
  }

  @Test
  @DisplayName(
      "[POST /api/orders/{orderId}/cancel] Buyer hủy đơn hàng thành công khi trạng thái hợp lệ")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void cancelMyOrder_Success_WhenStatusIsCancellable() throws Exception {
    Order order =
        createTestOrder(
            testBuyer,
            testFarmer,
            OrderStatus.CONFIRMED,
            PaymentStatus.PENDING); // CONFIRMED vẫn hủy được

    mockMvc
        .perform(post("/api/orders/{orderId}/cancel", order.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.status", is(OrderStatus.CANCELLED.name())));

    Order cancelledOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    // Kiểm tra hoàn kho
    Product productAfter = productRepository.findById(testProduct.getId()).orElseThrow();
    assertThat(productAfter.getStockQuantity()).isEqualTo(50 + 3); // Hoàn lại 3
  }

  @Test
  @DisplayName(
      "[POST /api/orders/{orderId}/cancel] Buyer hủy đơn hàng thất bại khi trạng thái không hợp lệ")
  @WithMockUser(
      username = "buyer.test@example.com",
      roles = {"CONSUMER"})
  void cancelMyOrder_Fail_WhenStatusIsNotCancellable() throws Exception {
    Order order =
        createTestOrder(
            testBuyer,
            testFarmer,
            OrderStatus.SHIPPING,
            PaymentStatus.PENDING); // SHIPPING không hủy được

    mockMvc
        .perform(post("/api/orders/{orderId}/cancel", order.getId()))
        .andExpect(status().isBadRequest()) // Mong đợi 400 Bad Request
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.message", containsString("Order cannot be cancelled")));
  }

  // --- Helper method để tạo đơn hàng test ---
  private Order createTestOrder(
      User buyer, User farmer, OrderStatus initialStatus, PaymentStatus paymentStatus) {
    Order order = new Order();
    order.setBuyer(buyer);
    order.setFarmer(farmer);
    order.setOrderType(OrderType.B2C);
    order.setOrderCode("TEST-" + UUID.randomUUID().toString().substring(0, 8));
    order.setPaymentMethod(PaymentMethod.COD);
    order.setPaymentStatus(paymentStatus);
    order.setStatus(initialStatus);
    // Sao chép địa chỉ
    order.setShippingFullName(testAddress.getFullName());
    order.setShippingPhoneNumber(testAddress.getPhoneNumber());
    order.setShippingAddressDetail(testAddress.getAddressDetail());
    order.setShippingProvinceCode(testAddress.getProvinceCode());
    order.setShippingDistrictCode(testAddress.getDistrictCode());
    order.setShippingWardCode(testAddress.getWardCode());

    order.setSubTotal(new BigDecimal("30000.00"));
    order.setShippingFee(BigDecimal.ZERO);
    order.setDiscountAmount(BigDecimal.ZERO);
    order.setTotalAmount(new BigDecimal("30000.00"));

    OrderItem item = new OrderItem();
    item.setProduct(testProduct); // Dùng testProduct đã tạo
    item.setProductName(testProduct.getName());
    item.setUnit(testProduct.getUnit());
    item.setPricePerUnit(testProduct.getPrice());
    item.setQuantity(3); // Số lượng cố định cho test case này
    item.setTotalPrice(testProduct.getPrice().multiply(BigDecimal.valueOf(3)));
    order.addOrderItem(item);

    return orderRepository.saveAndFlush(order); // Lưu và flush để có ID
  }
}
