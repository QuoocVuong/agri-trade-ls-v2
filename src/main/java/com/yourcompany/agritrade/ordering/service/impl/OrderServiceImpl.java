package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.*;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.common.util.VnPayUtils;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.*;
import com.yourcompany.agritrade.ordering.dto.response.*;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.*;
import com.yourcompany.agritrade.ordering.repository.specification.OrderSpecifications;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import com.yourcompany.agritrade.ordering.service.OrderService;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;

  private final PaymentRepository paymentRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final OrderMapper orderMapper;

  private final NotificationService notificationService;
  private final FarmerProfileRepository farmerProfileRepository;

  private final InvoiceService invoiceService;

  private final InvoiceRepository invoiceRepository;

  private final FileStorageService fileStorageService;

  private final SupplyOrderRequestRepository supplyOrderRequestRepository;

  @Value("${app.bank.accountName}")
  private String appBankAccountName;

  @Value("${app.bank.accountNumber}")
  private String appBankAccountNumber;

  @Value("${app.bank.nameDisplay}")
  private String appBankNameDisplay;

  @Value("${app.bank.bin}")
  private String appBankBin; // Mã BIN ngân hàng

  @Value("${app.bank.qr.serviceUrlBase:#{null}}")
  private String qrServiceUrlBase;

  @Value("${app.bank.qr.template:compact2}")
  private String qrTemplate;

  private final @Qualifier("vnPayService") PaymentGatewayService vnPayService;
  private final @Qualifier("moMoService") PaymentGatewayService moMoService;

  @Value("${app.frontend.url}")
  private String frontendAppUrl;

  @Value("${app.backend.url}")
  private String backendAppUrl;

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  @Retryable(
      retryFor = {
        OptimisticLockingFailureException.class,
        ObjectOptimisticLockingFailureException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 100))
  public List<OrderResponse> checkout(Authentication authentication, CheckoutRequest request) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();
    Address shippingAddress =
        addressRepository
            .findByIdAndUserId(request.getShippingAddressId(), buyer.getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Shipping Address", "id", request.getShippingAddressId()));

    List<CartItem> cartItems = cartItemRepository.findByUserId(buyer.getId());
    if (cartItems.isEmpty()) {
      throw new BadRequestException("Giỏ hàng của bạn đang trống.");
    }

    // --- BƯỚC 1: XÁC THỰC TOÀN BỘ GIỎ HÀNG VÀ TÍNH TOÁN LẠI TỔNG TIỀN ---
    List<String> validationErrors = new ArrayList<>();
    Map<Long, Product> validatedProducts = new HashMap<>(); // Lưu các sản phẩm đã được kiểm tra
    BigDecimal currentActualSubTotal = BigDecimal.ZERO;

    for (CartItem cartItem : cartItems) {
      Product productInCart = cartItem.getProduct();
      if (productInCart == null) {
        validationErrors.add("Một sản phẩm trong giỏ hàng không hợp lệ.");
        cartItemRepository.delete(cartItem);
        continue;
      }

      Long productId = productInCart.getId();
      int requestedQuantity = cartItem.getQuantity();

      Product productFromDb = productRepository.findById(productId).orElse(null);

      // Kiểm tra các điều kiện và thu thập lỗi
      if (productFromDb == null || productFromDb.isDeleted()) {
        validationErrors.add("Sản phẩm '" + productInCart.getName() + "' không còn tồn tại.");
        cartItemRepository.delete(cartItem);
        continue;
      }
      if (productFromDb.getStatus() != ProductStatus.PUBLISHED) {
        validationErrors.add("Sản phẩm '" + productFromDb.getName() + "' hiện không còn được bán.");
        cartItemRepository.delete(cartItem);
        continue;
      }
      if (productFromDb.getStockQuantity() < requestedQuantity) {
        validationErrors.add(
            "Sản phẩm '"
                + productFromDb.getName()
                + "' không đủ số lượng tồn kho (chỉ còn "
                + productFromDb.getStockQuantity()
                + ").");
        continue; // Không xóa, chỉ báo lỗi để người dùng sửa
      }

      // Nếu sản phẩm hợp lệ, thêm vào map để xử lý sau và tính tổng phụ
      validatedProducts.put(productId, productFromDb);
      currentActualSubTotal =
          currentActualSubTotal.add(
              productFromDb.getPrice().multiply(BigDecimal.valueOf(requestedQuantity)));
    }

    // Nếu có bất kỳ lỗi nào về tồn kho hoặc trạng thái, dừng lại ngay
    if (!validationErrors.isEmpty()) {
      String combinedErrorMessage = String.join("\n", validationErrors);
      throw new BadRequestException(
          "Không thể đặt hàng. Vui lòng kiểm tra lại giỏ hàng:\n" + combinedErrorMessage);
    }

    // --- BƯỚC 2: KIỂM TRA TỔNG TIỀN CUỐI CÙNG ---
    BigDecimal totalShippingFee = calculateTotalShippingFee(cartItems, buyer, shippingAddress);
    BigDecimal totalDiscount = calculateDiscount(buyer, currentActualSubTotal);
    BigDecimal currentActualTotal =
        currentActualSubTotal
            .add(totalShippingFee)
            .subtract(totalDiscount)
            .setScale(2, RoundingMode.HALF_UP);

    if (request.getConfirmedTotalAmount().compareTo(currentActualTotal) != 0) {
      log.warn(
          "Price/Total mismatch during checkout for user {}. Confirmed: {}, Actual: {}",
          buyer.getEmail(),
          request.getConfirmedTotalAmount(),
          currentActualTotal);
      throw new BadRequestException(
          "Tổng giá trị đơn hàng đã thay đổi do cập nhật giá hoặc phí vận chuyển. Vui lòng quay lại giỏ hàng để xác nhận lại.");
    }

    // --- BƯỚC 3: TẠO ĐƠN HÀNG (KHI MỌI THỨ ĐỀU HỢP LỆ) ---
    Map<Long, List<CartItem>> itemsByFarmer =
        cartItems.stream()
            .collect(Collectors.groupingBy(item -> item.getProduct().getFarmer().getId()));

    List<Order> createdOrders = new ArrayList<>();

    for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
      Long farmerId = entry.getKey();
      List<CartItem> farmerCartItems = entry.getValue();
      User farmer =
          userRepository
              .findById(farmerId)
              .orElseThrow(
                  () -> new IllegalStateException("Farmer not found during order creation"));
      FarmerProfile farmerProfile =
          farmerProfileRepository
              .findById(farmerId)
              .orElseThrow(() -> new IllegalStateException("Farmer profile not found"));

      Order order = new Order();
      order.setBuyer(buyer);
      order.setFarmer(farmer);
      order.setOrderType(determineOrderType(buyer, farmerCartItems));
      order.setOrderCode(generateOrderCode());
      order.setPaymentMethod(request.getPaymentMethod());
      order.setPaymentStatus(PaymentStatus.PENDING);
      order.setStatus(OrderStatus.PENDING);
      order.setNotes(request.getNotes());
      copyShippingAddress(order, shippingAddress);

      BigDecimal farmerSubTotal = BigDecimal.ZERO;

      for (CartItem cartItem : farmerCartItems) {
        Product product = validatedProducts.get(cartItem.getProduct().getId());
        int requestedQuantity = cartItem.getQuantity();

        // Trừ tồn kho trên đối tượng đã được xác thực
        product.setStockQuantity(product.getStockQuantity() - requestedQuantity);

        // Thêm dòng này để lưu lại sự thay đổi tồn kho của sản phẩm
        productRepository.saveAndFlush(product);

        // Tạo OrderItem với giá hiện tại
        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setProductName(product.getName());
        orderItem.setUnit(determineUnit(product, order.getOrderType()));
        orderItem.setPricePerUnit(product.getPrice());
        orderItem.setQuantity(requestedQuantity);
        BigDecimal itemTotalPrice =
            product.getPrice().multiply(BigDecimal.valueOf(requestedQuantity));
        orderItem.setTotalPrice(itemTotalPrice);

        order.addOrderItem(orderItem);
        farmerSubTotal = farmerSubTotal.add(itemTotalPrice);
      }

      // Tính toán các loại phí cho từng đơn hàng của từng farmer
      BigDecimal farmerShippingFee =
          calculateShippingFee(
              shippingAddress, farmerProfile, farmerCartItems, order.getOrderType());
      BigDecimal farmerDiscount =
          calculateDiscount(buyer, farmerSubTotal); // Discount có thể tính trên từng đơn hoặc tổng

      order.setSubTotal(farmerSubTotal);
      order.setShippingFee(farmerShippingFee);
      order.setDiscountAmount(farmerDiscount);
      order.setTotalAmount(farmerSubTotal.add(farmerShippingFee).subtract(farmerDiscount));

      Order savedOrder = orderRepository.save(order);
      createdOrders.add(savedOrder);

      notificationService.sendOrderPlacementNotification(savedOrder);
      createInitialPaymentRecord(savedOrder);
      if (savedOrder.getPaymentMethod() == PaymentMethod.INVOICE) {
        invoiceService.getOrCreateInvoiceForOrder(savedOrder);
      }
      log.info("Order {} created successfully for farmer {}", savedOrder.getOrderCode(), farmerId);
    }

    // Xóa toàn bộ giỏ hàng sau khi đã xử lý thành công
    cartItemRepository.deleteAllInBatch(cartItems);

    return createdOrders.stream()
        .map(o -> orderRepository.findById(o.getId()).orElse(o))
        .map(orderMapper::toOrderResponse)
        .collect(Collectors.toList());
  }

  private BigDecimal calculateTotalShippingFee(
      List<CartItem> cartItems, User buyer, Address shippingAddress) {
    Map<Long, List<CartItem>> itemsByFarmer =
        cartItems.stream()
            .collect(Collectors.groupingBy(item -> item.getProduct().getFarmer().getId()));

    BigDecimal totalShippingFee = BigDecimal.ZERO;

    for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
      Long farmerId = entry.getKey();
      List<CartItem> farmerCartItems = entry.getValue();
      // Lấy profile để tính phí ship
      FarmerProfile farmerProfile = farmerProfileRepository.findById(farmerId).orElse(null);
      OrderType orderType = determineOrderType(buyer, farmerCartItems);

      if (farmerProfile != null) {
        totalShippingFee =
            totalShippingFee.add(
                calculateShippingFee(shippingAddress, farmerProfile, farmerCartItems, orderType));
      }
    }
    return totalShippingFee;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<OrderSummaryResponse> getMyOrdersAsBuyer(
      Authentication authentication,
      String keyword,
      OrderStatus status,
      PaymentMethod paymentMethod,
      PaymentStatus paymentStatus,
      OrderType orderType,
      Pageable pageable) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();
    Specification<Order> spec =
        Specification.where(OrderSpecifications.byBuyer(buyer.getId()))
            .and(OrderSpecifications.hasStatus(status))
            .and(OrderSpecifications.buyerSearch(keyword))
            .and(OrderSpecifications.hasPaymentMethod(paymentMethod))
            .and(OrderSpecifications.hasPaymentStatus(paymentStatus))
            .and(OrderSpecifications.hasOrderType(orderType));

    Page<Order> orderPage = orderRepository.findAll(spec, pageable);
    return orderMapper.toOrderSummaryResponsePage(orderPage);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<OrderSummaryResponse> getMyOrdersAsFarmer(
      Authentication authentication,
      String keyword,
      OrderStatus status,
      PaymentMethod paymentMethod,
      PaymentStatus paymentStatus,
      OrderType orderType,
      Pageable pageable) {
    User farmer = SecurityUtils.getCurrentAuthenticatedUser();
    Specification<Order> spec =
        Specification.where(OrderSpecifications.byFarmer(farmer.getId()))
            .and(OrderSpecifications.hasStatus(status))
            .and(OrderSpecifications.farmerSearch(keyword)) // Sử dụng farmerSearch
            .and(OrderSpecifications.hasPaymentMethod(paymentMethod))
            .and(OrderSpecifications.hasPaymentStatus(paymentStatus))
            .and(OrderSpecifications.hasOrderType(orderType));

    Page<Order> orderPage = orderRepository.findAll(spec, pageable);
    return orderMapper.toOrderSummaryResponsePage(orderPage);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<OrderSummaryResponse> getAllOrdersForAdmin(
      String keyword,
      OrderStatus status,
      PaymentMethod paymentMethod,
      PaymentStatus paymentStatus,
      OrderType orderType,
      Long buyerId,
      Long farmerId,
      Pageable pageable) {
    Specification<Order> spec =
        Specification.where(OrderSpecifications.hasStatus(status))
            .and(OrderSpecifications.byBuyer(buyerId))
            .and(OrderSpecifications.byFarmer(farmerId))
            .and(OrderSpecifications.hasPaymentMethod(paymentMethod))
            .and(OrderSpecifications.hasPaymentStatus(paymentStatus))
            .and(OrderSpecifications.hasOrderType(orderType));

    if (StringUtils.hasText(keyword)) {
      // Keyword có thể là mã đơn hàng, tên người mua, hoặc tên người bán
      spec =
          spec.and(
              Specification.anyOf(
                  OrderSpecifications.hasOrderCode(keyword),
                  OrderSpecifications.hasBuyerName(keyword),
                  OrderSpecifications.hasFarmerName(keyword)));
    }

    Page<Order> orderPage = orderRepository.findAll(spec, pageable); // Dùng findAll với Spec
    return orderMapper.toOrderSummaryResponsePage(orderPage);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse getOrderDetails(Authentication authentication, Long orderId) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    boolean isAdmin =
        authentication
            .getAuthorities()
            .contains(new SimpleGrantedAuthority(RoleType.ROLE_ADMIN.name()));

    if (!isAdmin
        && !order.getBuyer().getId().equals(user.getId())
        && !order.getFarmer().getId().equals(user.getId())) {
      throw new AccessDeniedException("User does not have permission to view this order");
    }

    populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP

    return orderMapper.toOrderResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse getOrderDetailsByCode(Authentication authentication, String orderCode) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Order order =
        orderRepository
            .findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "code", orderCode));

    boolean isAdmin =
        authentication
            .getAuthorities()
            .contains(new SimpleGrantedAuthority(RoleType.ROLE_ADMIN.name()));

    if (!isAdmin
        && !order.getBuyer().getId().equals(user.getId())
        && !order.getFarmer().getId().equals(user.getId())) {
      throw new AccessDeniedException("User does not have permission to view this order");
    }
    populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP
    return orderMapper.toOrderResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse getOrderDetailsForAdmin(Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP
    return orderMapper.toOrderResponse(order);
  }

  @Override
  @Transactional
  public OrderResponse updateOrderStatus(
      Authentication authentication, Long orderId, OrderStatusUpdateRequest request) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_ADMIN);
    boolean isFarmer = user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_FARMER);

    // Kiểm tra quyền cập nhật
    if (!isAdmin && !(isFarmer && order.getFarmer().getId().equals(user.getId()))) {
      throw new AccessDeniedException("User does not have permission to update this order status");
    }

    OrderStatus currentStatus = order.getStatus();
    OrderStatus newStatus = request.getStatus();

    // *** Sử dụng helper method để kiểm tra chuyển đổi trạng thái ***
    if (!isValidStatusTransition(currentStatus, newStatus, isAdmin, isFarmer)) {
      throw new BadRequestException(
          "Invalid status transition from "
              + currentStatus
              + " to "
              + newStatus
              + " by "
              + (isAdmin ? "Admin" : "Farmer"));
    }

    order.setStatus(newStatus);
    // Cập nhật trạng thái thanh toán nếu giao hàng COD thành công
    if (newStatus == OrderStatus.DELIVERED
        && order.getPaymentMethod() == PaymentMethod.COD
        && order.getPaymentStatus() == PaymentStatus.PENDING) {
      order.setPaymentStatus(PaymentStatus.PAID);
      // Tạo payment record cho COD nếu cần
      createCodPaymentRecord(order);
    }

    Order updatedOrder = orderRepository.save(order);
    log.info("Order {} status updated to {} by user {}", orderId, newStatus, user.getId());
    // Gửi thông báo cập nhật trạng thái
    notificationService.sendOrderStatusUpdateNotification(
        updatedOrder, currentStatus); // Gọi hàm gửi thông báo

    return getOrderDetails(authentication, orderId); // Load lại đầy đủ để trả về
  }

  @Override
  @Transactional
  @Retryable(
      retryFor = {
        OptimisticLockingFailureException.class,
        ObjectOptimisticLockingFailureException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 100, multiplier = 2) // Tăng thời gian chờ giữa các lần thử lại
      )
  public OrderResponse cancelOrder(Authentication authentication, Long orderId) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();

    // Bước 1: Lấy thông tin đơn hàng cùng với các mục hàng và người mua/bán
    // Sử dụng một phương thức repository để tối ưu
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    // Bước 2: Kiểm tra quyền hủy đơn hàng
    validateCancellationPermission(currentUser, order);

    // Bước 3: Kiểm tra trạng thái đơn hàng có cho phép hủy không
    validateCancellableStatus(order);

    // Bước 4: Hoàn trả tồn kho cho các sản phẩm trong đơn hàng
    // Tách ra một phương thức riêng để xử lý logic này, có thể với transaction riêng nếu cần độ
    // phức tạp cao hơn
    restoreStockForCancelledOrder(order);

    // Bước 5: Cập nhật trạng thái đơn hàng và trạng thái thanh toán
    OrderStatus previousOrderStatus = order.getStatus(); // Lưu lại trạng thái cũ để gửi thông báo
    order.setStatus(OrderStatus.CANCELLED);
    updatePaymentStatusForCancelledOrder(order);

    Order cancelledOrder = orderRepository.save(order); // Lưu lại đơn hàng đã hủy
    log.info(
        "Order {} cancelled by user {}. Previous status: {}",
        cancelledOrder.getOrderCode(),
        currentUser.getEmail(),
        previousOrderStatus);

    // Bước 6: Gửi thông báo hủy đơn
    // (NotificationService nên tự xử lý việc gửi cho buyer và farmer)
    notificationService.sendOrderCancellationNotification(cancelledOrder);

    // Bước 7: Load lại đầy đủ thông tin và trả về

    Order finalCancelledOrder =
        orderRepository
            .findById(cancelledOrder.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cancelled order not found after saving: " + cancelledOrder.getId()));
    // Đảm bảo hình ảnh sản phẩm được populate nếu OrderResponse cần
    populateProductImageUrlsInOrder(finalCancelledOrder);
    return orderMapper.toOrderResponse(finalCancelledOrder);
  }

  private void validateCancellationPermission(User currentUser, Order order) {
    boolean isAdmin = SecurityUtils.hasRole(RoleType.ROLE_ADMIN.name()); // Sử dụng SecurityUtils
    boolean isBuyer =
        currentUser.getRoles().stream()
            .anyMatch(
                r ->
                    r.getName() == RoleType.ROLE_CONSUMER
                        || r.getName() == RoleType.ROLE_BUSINESS_BUYER);

    if (!isAdmin && !(isBuyer && order.getBuyer().getId().equals(currentUser.getId()))) {
      throw new AccessDeniedException(
          "User does not have permission to cancel this order: " + order.getOrderCode());
    }
  }

  private void validateCancellableStatus(Order order) {
    if (order.getStatus() != OrderStatus.PENDING
        && order.getStatus() != OrderStatus.CONFIRMED
        && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
      // Thêm AWAITING_PAYMENT vào các trạng thái có thể hủy
      throw new BadRequestException(
          "Order "
              + order.getOrderCode()
              + " cannot be cancelled in its current status: "
              + order.getStatus());
    }
  }

  @Transactional // Sẽ tham gia vào transaction của cancelOrder
  protected void restoreStockForCancelledOrder(Order order) {
    if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
      log.warn("No items found in order {} to restore stock.", order.getOrderCode());
      return;
    }

    for (OrderItem item : order.getOrderItems()) {
      if (item.getProduct() == null) {
        log.warn(
            "OrderItem {} in order {} has no associated product. Skipping stock restoration.",
            item.getId(),
            order.getOrderCode());
        continue;
      }
      Long productId = item.getProduct().getId();
      int quantityToRestore = item.getQuantity();

      // Tải lại Product trong transaction để lấy version mới nhất và khóa
      // Hoặc dựa vào optimistic lock với @Version trên Product entity
      Product product =
          productRepository.findById(productId).orElse(null); // Không ném lỗi ngay, xử lý ở dưới

      if (product != null) {
        // Chỉ hoàn kho nếu sản phẩm chưa bị xóa vĩnh viễn
        if (product.isDeleted()) {
          log.warn(
              "Product ID {} for order item {} is soft-deleted. Stock will not be restored.",
              productId,
              item.getId());
          continue;
        }

        log.debug(
            "Restoring stock for product {}: current={}, restoring={}",
            productId,
            product.getStockQuantity(),
            quantityToRestore);
        product.setStockQuantity(product.getStockQuantity() + quantityToRestore);
        try {
          productRepository.saveAndFlush(product); // Flush để phát hiện lỗi sớm
        } catch (OptimisticLockingFailureException e) {
          log.warn(
              "Optimistic lock failed while restoring stock for product {} in order {}. Retrying (handled by @Retryable)...",
              productId,
              order.getOrderCode());
          throw e; // Ném lại để @Retryable xử lý
        } catch (Exception e) {
          log.error(
              "Error saving product {} while restoring stock for order {}: {}",
              productId,
              order.getOrderCode(),
              e.getMessage(),
              e);
          // Quyết định nghiệp vụ:
          // 1. Ném lỗi, rollback toàn bộ cancelOrder:
          throw new RuntimeException(
              "Failed to restore stock for product " + productId + ". Order cancellation failed.",
              e);
        }
      } else {
        log.warn(
            "Product with id {} not found while trying to restore stock for cancelled order {}. Stock for this item cannot be restored.",
            productId,
            order.getOrderCode());
        // Quyết định nghiệp vụ:
        // 1. Ném lỗi, rollback toàn bộ cancelOrder:
        throw new IllegalStateException(
            "Product "
                + productId
                + " associated with order item "
                + item.getId()
                + " not found. Cannot cancel order.");
      }
    }
  }

  private void updatePaymentStatusForCancelledOrder(Order order) {
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      Payment successfulPayment =
          findSuccessfulPaymentForOrder(order); // Tìm giao dịch thanh toán thành công

      if (successfulPayment != null) {
        boolean refundRequested = false;
        try {
          PaymentGatewayService gatewayService =
              getPaymentGatewayService(
                  successfulPayment.getPaymentGateway()); // Lấy service tương ứng
          if (gatewayService != null) {
            // Số tiền hoàn có thể là toàn bộ hoặc một phần (nếu chính sách cho phép)
            refundRequested =
                gatewayService.requestRefund(
                    successfulPayment.getTransactionCode(),
                    successfulPayment.getAmount(), // Hoàn toàn bộ số tiền của giao dịch đó
                    "Order "
                        + order.getOrderCode()
                        + " cancelled by "
                        + SecurityUtils.getCurrentAuthenticatedUser().getFullName());
          } else {
            log.warn(
                "No payment gateway service found for gateway: {}",
                successfulPayment.getPaymentGateway());
          }
        } catch (Exception e) {
          log.error(
              "Error requesting refund for order {} via gateway {}: {}",
              order.getOrderCode(),
              successfulPayment.getPaymentGateway(),
              e.getMessage(),
              e);
          // Xử lý lỗi khi gọi API hoàn tiền (ví dụ: lưu lại để thử lại, thông báo admin)
        }

        if (refundRequested) {
          order.setPaymentStatus(PaymentStatus.REFUND_PENDING); // Hoặc REFUND_REQUESTED
          successfulPayment.setStatus(
              PaymentTransactionStatus.REFUND_REQUESTED); // Cập nhật trạng thái giao dịch gốc
          log.info(
              "Refund requested for order {}. Payment status set to REFUND_PENDING.",
              order.getOrderCode());
        } else {
          // Nếu không thể yêu cầu hoàn tiền tự động (lỗi API, cổng không hỗ trợ)
          order.setPaymentStatus(
              PaymentStatus.REFUND_MANUAL_REQUIRED); // Một trạng thái mới để admin xử lý
          log.warn(
              "Failed to automatically request refund for order {}. Manual refund required.",
              order.getOrderCode());
        }
        paymentRepository.save(successfulPayment);
      } else {
        log.warn(
            "No successful payment record found for PAID order {} to refund.",
            order.getOrderCode());
        order.setPaymentStatus(PaymentStatus.REFUND_MANUAL_REQUIRED); // Cần admin kiểm tra
      }

    } else if (order.getPaymentStatus() == PaymentStatus.PENDING
        || order.getPaymentStatus() == PaymentStatus.AWAITING_PAYMENT_TERM) {
      order.setPaymentStatus(
          PaymentStatus.FAILED); // Hoặc một trạng thái hủy cụ thể hơn như CANCELLED
      // Cập nhật các Payment record liên quan (nếu có) sang CANCELLED hoặc FAILED
      order.getPayments().stream()
          .filter(p -> p.getStatus() == PaymentTransactionStatus.PENDING)
          .forEach(
              p -> {
                p.setStatus(PaymentTransactionStatus.CANCELLED); // Hoặc FAILED
                p.setGatewayMessage("Payment cancelled due to order cancellation.");
                paymentRepository.save(p);
              });
      log.info(
          "Order {} cancelled. Payment status set to FAILED as it was pending or awaiting term.",
          order.getOrderCode());
    }
  }

  // Helper để lấy PaymentGatewayService tương ứng
  private PaymentGatewayService getPaymentGatewayService(String gatewayName) {
    if (PaymentMethod.VNPAY.name().equalsIgnoreCase(gatewayName)) {
      return vnPayService; // Đã inject
    } else if (PaymentMethod.MOMO.name().equalsIgnoreCase(gatewayName)) {
      return moMoService; // Đã inject
    }
    // Thêm các cổng khác nếu có
    return null;
  }

  // Helper để tìm giao dịch thanh toán thành công của đơn hàng
  private Payment findSuccessfulPaymentForOrder(Order order) {
    return order.getPayments().stream()
        .filter(p -> p.getStatus() == PaymentTransactionStatus.SUCCESS)
        .findFirst()
        .orElse(null);
  }

  @Override
  public String generateOrderCode() {
    // Ví dụ: LS + Năm + Tháng + Ngày + 4 số ngẫu nhiên
    String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    int randomPart = ThreadLocalRandom.current().nextInt(1000, 10000);
    return "AGT" + datePart + "-" + randomPart;
  }

  private void copyShippingAddress(Order order, Address address) {
    order.setShippingFullName(address.getFullName());
    order.setShippingPhoneNumber(address.getPhoneNumber());
    order.setShippingAddressDetail(address.getAddressDetail());
    order.setShippingProvinceCode(address.getProvinceCode());
    order.setShippingDistrictCode(address.getDistrictCode());
    order.setShippingWardCode(address.getWardCode());
  }

  private OrderType determineOrderType(User buyer, List<CartItem> items) {
    // 1. Kiểm tra xem người mua có vai trò BUSINESS_BUYER không.
    boolean isBusinessBuyer =
        buyer.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER);

    // 2. Nếu người mua không phải là Business Buyer, đơn hàng luôn là B2C.
    if (!isBusinessBuyer) {
      return OrderType.B2C;
    }

    // 3. Nếu là Business Buyer, kiểm tra các sản phẩm trong giỏ hàng.
    //    Đơn hàng chỉ là B2B nếu TẤT CẢ các sản phẩm đều được bật B2B.
    if (items == null || items.isEmpty()) {
      // Nếu giỏ hàng trống (trường hợp hiếm), mặc định là B2C.
      return OrderType.B2C;
    }

    // allMatch sẽ trả về true nếu tất cả các item thỏa mãn điều kiện,
    // hoặc trả về true nếu danh sách item rỗng (đã kiểm tra ở trên).
    boolean allItemsAreB2BEnabled =
        items.stream()
            .allMatch(item -> item.getProduct() != null && item.getProduct().isB2bEnabled());

    if (allItemsAreB2BEnabled) {
      // Nếu tất cả sản phẩm đều hỗ trợ B2B, đây là đơn hàng B2B.
      log.info("Determined order type as B2B for buyer ID {}", buyer.getId());
      return OrderType.B2B;
    } else {
      // Nếu có ít nhất một sản phẩm không hỗ trợ B2B, toàn bộ đơn hàng được coi là B2C.
      log.info(
          "Determined order type as B2C for buyer ID {} because at least one item is not B2B enabled.",
          buyer.getId());
      return OrderType.B2C;
    }
  }

  private BigDecimal determinePrice(Product product, int quantity, OrderType type) {
    // Mặc định trả về giá B2C
    return product.getPrice();
  }

  private String determineUnit(Product product, OrderType type) {
    if (type == OrderType.B2B
        && product.isB2bEnabled()
        && StringUtils.hasText(product.getWholesaleUnit())) {
      return product.getWholesaleUnit();
    }
    return product.getUnit();
  }

  private BigDecimal calculateShippingFee(
      Address shippingAddress,
      FarmerProfile farmerProfile,
      List<CartItem> items,
      OrderType orderType) {
    if (items == null || items.isEmpty() || shippingAddress == null || farmerProfile == null) {
      log.warn(
          "Cannot calculate shipping fee due to missing input: shippingAddress={}, farmerProfile={}, itemsEmpty={}",
          shippingAddress == null,
          farmerProfile == null,
          items == null || items.isEmpty());
      return BigDecimal.ZERO; // Hoặc ném lỗi nếu các thông tin này là bắt buộc
    }

    // --- Lấy mã tỉnh của người bán và người nhận ---
    String buyerProvinceCode = shippingAddress.getProvinceCode();
    String farmerProvinceCode = farmerProfile.getProvinceCode();

    if (buyerProvinceCode == null || farmerProvinceCode == null) {
      log.error(
          "Cannot calculate shipping fee: Missing province code. Buyer: {}, Farmer: {}",
          buyerProvinceCode,
          farmerProvinceCode);
      throw new BadRequestException(
          "Không thể xác định tỉnh/thành phố của người bán hoặc người nhận.");
    }

    // --- Xác định giao nội tỉnh hay ngoại tỉnh ---
    boolean isIntraProvinceShipment = buyerProvinceCode.equals(farmerProvinceCode);

    // --- Định nghĩa các mức phí  ---
    final BigDecimal INTRA_PROVINCE_FEE =
        new BigDecimal("15000.00"); // Phí giao nội tỉnh (B2C & B2B)
    final BigDecimal INTER_PROVINCE_FEE =
        new BigDecimal("30000.00"); // Phí giao ngoại tỉnh (Chỉ B2C)
    // (Tùy chọn) Có thể thêm phí theo trọng lượng/kích thước nếu cần
    final BigDecimal FEE_PER_KG_INTRA = new BigDecimal("3000.00");
    final BigDecimal FEE_PER_KG_INTER = new BigDecimal("5000.00");

    // --- Xử lý B2B ---
    if (orderType == OrderType.B2B) {
      if (!isIntraProvinceShipment) {
        // Nếu B2B mà khác tỉnh -> không cho phép
        log.warn(
            "B2B order rejected: Shipment between different provinces. Buyer Province: {}, Farmer Province: {}",
            buyerProvinceCode,
            farmerProvinceCode);
        throw new BadRequestException(
            "Đơn hàng doanh nghiệp chỉ hỗ trợ giao hàng trong cùng tỉnh với nông dân.");
      } else {
        // B2B nội tỉnh -> Áp dụng phí nội tỉnh
        log.debug("Calculating B2B intra-province shipping fee for order type {}", orderType);
        return INTRA_PROVINCE_FEE; // Trả về phí B2B nội tỉnh
      }
    }

    // --- Xử lý B2C ---
    if (isIntraProvinceShipment) {
      // B2C nội tỉnh
      log.debug("Calculating B2C intra-province shipping fee for order type {}", orderType);

      return INTRA_PROVINCE_FEE; // Trả về phí B2C nội tỉnh
    } else {
      // B2C ngoại tỉnh
      log.debug("Calculating B2C inter-province shipping fee for order type {}", orderType);

      return INTER_PROVINCE_FEE; // Trả về phí B2C ngoại tỉnh
    }
  }

  // Tách hàm tính tổng trọng lượng
  private double calculateTotalWeight(List<CartItem> items) {
    double totalWeightKg = 0;
    for (CartItem item : items) {
      Product product = item.getProduct(); // Cần đảm bảo product được load
      if (product != null && product.getWeightInGrams() != null && product.getWeightInGrams() > 0) {
        totalWeightKg += (product.getWeightInGrams() * item.getQuantity()) / 1000.0;
      }
    }
    return totalWeightKg;
  }

  // Tách hàm tính subTotal
  private BigDecimal calculateSubTotal(List<CartItem> items, OrderType orderType) {
    BigDecimal subTotal = BigDecimal.ZERO;
    for (CartItem cartItem : items) {
      Product product = cartItem.getProduct(); // Cần đảm bảo product được load
      if (product == null) continue; // Bỏ qua nếu product null
      int requestedQuantity = cartItem.getQuantity();
      BigDecimal pricePerUnit = determinePrice(product, requestedQuantity, orderType);
      BigDecimal itemTotalPrice = pricePerUnit.multiply(BigDecimal.valueOf(requestedQuantity));
      subTotal = subTotal.add(itemTotalPrice);
    }
    return subTotal;
  }

  private BigDecimal calculateDiscount(User buyer, BigDecimal subTotal) {

    BigDecimal discount = BigDecimal.ZERO;
    BigDecimal percentageDiscount = BigDecimal.ZERO;
    BigDecimal fixedDiscount = BigDecimal.ZERO;

    // --- Định nghĩa ngưỡng và tỷ lệ giảm giá (Ví dụ) ---
    final BigDecimal B2C_DISCOUNT_THRESHOLD = new BigDecimal("500000.00"); // 500k
    final BigDecimal B2C_DISCOUNT_PERCENTAGE = new BigDecimal("0.05"); // 5%

    final BigDecimal B2B_DISCOUNT_THRESHOLD_1 = new BigDecimal("2000000.00"); // 2 triệu
    final BigDecimal B2B_DISCOUNT_PERCENTAGE_1 = new BigDecimal("0.07"); // 7%
    final BigDecimal B2B_DISCOUNT_THRESHOLD_2 = new BigDecimal("5000000.00"); // 5 triệu
    final BigDecimal B2B_DISCOUNT_PERCENTAGE_2 = new BigDecimal("0.10"); // 10%

    boolean isBusiness =
        buyer.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_BUSINESS_BUYER);

    if (isBusiness) {
      if (subTotal.compareTo(B2B_DISCOUNT_THRESHOLD_2) >= 0) {
        discount = subTotal.multiply(B2B_DISCOUNT_PERCENTAGE_2);
      } else if (subTotal.compareTo(B2B_DISCOUNT_THRESHOLD_1) >= 0) {
        discount = subTotal.multiply(B2B_DISCOUNT_PERCENTAGE_1);
      }
    } else { // Assume Consumer
      if (subTotal.compareTo(B2C_DISCOUNT_THRESHOLD) >= 0) {
        discount = subTotal.multiply(B2C_DISCOUNT_PERCENTAGE);
      }
    }

    // Kết hợp các loại giảm giá
    discount = percentageDiscount.max(fixedDiscount);

    // Làm tròn tiền giảm giá
    if (discount.compareTo(BigDecimal.ZERO) > 0) {
      discount = discount.setScale(0, RoundingMode.DOWN); // Làm tròn xuống đơn vị đồng
      log.info("Applied discount of {} for order subtotal {}", discount, subTotal);
    }

    return discount;
  }

  private boolean isValidStatusTransition(
      OrderStatus current, OrderStatus next, boolean isAdmin, boolean isFarmer) {
    if (current == next) return false; // Không được cập nhật thành chính nó
    if (current == OrderStatus.DELIVERED
        || current == OrderStatus.CANCELLED
        || current == OrderStatus.RETURNED) {
      // Không đổi trạng thái từ các trạng thái cuối cùng
      log.warn("Attempt to change status from final state: {} to {}", current, next);
      return false;
    }

    switch (current) {
      case PENDING:
        // Từ PENDING: Admin/Farmer có thể CONFIRMED, Admin/Buyer có thể CANCELLED
        return next == OrderStatus.CONFIRMED && (isAdmin || isFarmer);
        // Việc cancel sẽ xử lý ở API cancelOrder
      case CONFIRMED:
        // Từ CONFIRMED: Farmer có thể PROCESSING, Admin/Buyer có thể CANCELLED
        return (next == OrderStatus.PROCESSING && (isAdmin || isFarmer));
        // Việc cancel sẽ xử lý ở API cancelOrder
      case PROCESSING:
        // Từ PROCESSING: Farmer/Admin có thể SHIPPING
        return next == OrderStatus.SHIPPING && (isAdmin || isFarmer);
      case SHIPPING:
        // Từ SHIPPING: Farmer/Admin có thể DELIVERED

        return next == OrderStatus.DELIVERED && (isAdmin || isFarmer);
      default:
        return false; // Các trường hợp khác không hợp lệ
    }
  }

  private void createCodPaymentRecord(Order order) {
    Payment codPayment = new Payment();
    codPayment.setOrder(order);
    codPayment.setAmount(order.getTotalAmount());
    codPayment.setPaymentGateway(PaymentMethod.COD.name());
    codPayment.setStatus(PaymentTransactionStatus.SUCCESS); // COD thành công khi đơn hàng delivered
    codPayment.setPaymentTime(LocalDateTime.now());
    codPayment.setGatewayMessage("Paid on delivery");
    paymentRepository.save(codPayment);
  }

  // Helper tạo Payment Record ban đầu
  private void createInitialPaymentRecord(Order order) {
    Payment initialPayment = new Payment();
    initialPayment.setOrder(order);
    initialPayment.setAmount(order.getTotalAmount());
    initialPayment.setPaymentGateway(order.getPaymentMethod().name());
    PaymentTransactionStatus initialStatus;
    PaymentStatus orderPaymentStatus = PaymentStatus.PENDING;

    switch (order.getPaymentMethod()) {
      case COD:
      case BANK_TRANSFER: // Chuyển khoản cũng cần xác nhận nên là PENDING ban đầu
        initialStatus = PaymentTransactionStatus.PENDING;
        orderPaymentStatus = PaymentStatus.PENDING;
        break;
      case INVOICE: // Nếu dùng công nợ
        initialStatus = PaymentTransactionStatus.PENDING; // Giao dịch payment có thể vẫn là pending
        orderPaymentStatus =
            PaymentStatus.AWAITING_PAYMENT_TERM; // Nhưng trạng thái đơn hàng là chờ công nợ
        break;
      case VNPAY:
      case MOMO:
      case ZALOPAY:
        initialStatus = PaymentTransactionStatus.PENDING; // Chờ callback từ cổng thanh toán
        orderPaymentStatus = PaymentStatus.PENDING;
        break;
      default:
        initialStatus = PaymentTransactionStatus.PENDING;
        orderPaymentStatus = PaymentStatus.PENDING;
    }
    initialPayment.setStatus(initialStatus);
    order.setPaymentStatus(orderPaymentStatus);

    paymentRepository.save(initialPayment);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderCalculationResponse calculateOrderTotals(
      Authentication authentication, OrderCalculationRequest request) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();
    List<CartItem> cartItems = cartItemRepository.findByUserId(buyer.getId());
    if (cartItems.isEmpty()) {
      // Trả về giá trị 0 nếu giỏ hàng trống
      return new OrderCalculationResponse(
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, OrderType.B2C);
    }

    Address shippingAddress = null;
    if (request != null && request.getShippingAddressId() != null) {
      shippingAddress =
          addressRepository
              .findByIdAndUserId(request.getShippingAddressId(), buyer.getId())
              .orElse(null);
    }
    // Nếu không có addressId, có thể lấy địa chỉ mặc định của user
    if (shippingAddress == null) {
      shippingAddress = addressRepository.findByUserIdAndIsDefaultTrue(buyer.getId()).orElse(null);
    }

    // Phân nhóm theo farmer
    Map<Long, List<CartItem>> itemsByFarmer =
        cartItems.stream()
            .collect(Collectors.groupingBy(item -> item.getProduct().getFarmer().getId()));

    BigDecimal totalSubTotal = BigDecimal.ZERO;
    BigDecimal totalShippingFee = BigDecimal.ZERO;
    BigDecimal totalDiscount = BigDecimal.ZERO;
    OrderType finalOrderType = OrderType.B2C;

    for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
      Long farmerId = entry.getKey();
      List<CartItem> farmerCartItems = entry.getValue();
      FarmerProfile farmerProfile = farmerProfileRepository.findById(farmerId).orElse(null);

      // Xác định OrderType
      finalOrderType = determineOrderType(buyer, farmerCartItems);

      BigDecimal farmerSubTotal = BigDecimal.ZERO;
      for (CartItem cartItem : farmerCartItems) {
        Product product = cartItem.getProduct();
        if (product == null) continue;
        int quantity = cartItem.getQuantity();
        BigDecimal price = determinePrice(product, quantity, finalOrderType);
        farmerSubTotal = farmerSubTotal.add(price.multiply(BigDecimal.valueOf(quantity)));
      }

      // Tính phí ship cho farmer này
      BigDecimal farmerShippingFee = BigDecimal.ZERO;
      if (shippingAddress != null && farmerProfile != null) {
        farmerShippingFee =
            calculateShippingFee(shippingAddress, farmerProfile, farmerCartItems, finalOrderType);
      } else {
        log.warn(
            "Cannot calculate shipping fee for farmer {} due to missing address or profile",
            farmerId);
      }

      totalSubTotal = totalSubTotal.add(farmerSubTotal);
      totalShippingFee = totalShippingFee.add(farmerShippingFee);
    }

    // Tính discount tổng dựa trên tổng subTotal và buyer
    totalDiscount = calculateDiscount(buyer, totalSubTotal /*, request.getVoucherCode() */);
    BigDecimal totalAmount = totalSubTotal.add(totalShippingFee).subtract(totalDiscount);

    return new OrderCalculationResponse(
        totalSubTotal, totalShippingFee, totalDiscount, totalAmount, finalOrderType);
  }

  @Override
  @Transactional
  public OrderResponse confirmBankTransferPayment(Long orderId, String bankTransactionCode) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
      throw new BadRequestException("Order was not placed with Bank Transfer method.");
    }
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      throw new BadRequestException("Order has already been paid.");
    }

    order.setPaymentStatus(PaymentStatus.PAID);
    if (order.getStatus() == OrderStatus.PENDING
        || order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
      order.setStatus(OrderStatus.CONFIRMED);
    }

    // Tạo bản ghi Payment
    Payment payment = new Payment();
    payment.setOrder(order);
    payment.setAmount(order.getTotalAmount());
    payment.setPaymentGateway(PaymentMethod.BANK_TRANSFER.name());
    payment.setStatus(PaymentTransactionStatus.SUCCESS);
    payment.setPaymentTime(LocalDateTime.now());
    payment.setTransactionCode(bankTransactionCode); // Lưu mã giao dịch ngân hàng
    payment.setGatewayMessage("Payment confirmed manually by admin.");
    paymentRepository.save(payment);

    Order savedOrder = orderRepository.save(order);
    log.info("Bank transfer payment confirmed for order {}", order.getOrderCode());
    notificationService.sendPaymentSuccessNotification(savedOrder); // Thông báo cho buyer
    return orderMapper.toOrderResponse(savedOrder);
  }

  @Override
  @Transactional
  public OrderResponse confirmOrderPaymentByAdmin(
      Long orderId,
      PaymentMethod paymentMethodConfirmedByAdmin,
      String transactionReference,
      String adminNotes) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      throw new BadRequestException("Đơn hàng này đã được ghi nhận thanh toán.");
    }
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new BadRequestException("Không thể xác nhận thanh toán cho đơn hàng đã hủy.");
    }

    PaymentStatus originalPaymentStatus = order.getPaymentStatus();

    order.setPaymentStatus(PaymentStatus.PAID);

    // Đối với đơn hàng INVOICE, trạng thái đơn hàng (PROCESSING, SHIPPING, DELIVERED)
    // thường đã được cập nhật trước . Việc xác nhận thanh toán công nợ chủ yếu cập nhật
    // PaymentStatus.
    // Nếu đơn hàng là loại khác (ví dụ BANK_TRANSFER) và đang PENDING, thì mới chuyển OrderStatus.
    OrderStatus originalOrderStatus = order.getStatus();
    if (order.getPaymentMethod() != PaymentMethod.INVOICE
        && (order.getStatus() == OrderStatus.PENDING
            || order.getStatus() == OrderStatus.AWAITING_PAYMENT)) {
      order.setStatus(OrderStatus.CONFIRMED);
    }

    // Cập nhật trạng thái Invoice liên quan
    if (order.getPaymentMethod() == PaymentMethod.INVOICE) {
      invoiceRepository
          .findByOrderId(order.getId())
          .ifPresent(
              invoice -> {
                invoice.setStatus(InvoiceStatus.PAID);
                // invoice.setPaymentDate(LocalDate.now());
                invoiceRepository.save(invoice);
                log.info(
                    "Invoice {} for order {} marked as PAID.",
                    invoice.getInvoiceNumber(),
                    order.getOrderCode());
              });
    }

    // Tạo bản ghi Payment
    Payment payment = new Payment();
    payment.setOrder(order);
    payment.setAmount(order.getTotalAmount());
    payment.setPaymentGateway(paymentMethodConfirmedByAdmin.name());
    payment.setStatus(PaymentTransactionStatus.SUCCESS);
    payment.setPaymentTime(LocalDateTime.now());
    payment.setTransactionCode(transactionReference);
    payment.setGatewayMessage(
        "Payment confirmed by Admin for "
            + order.getPaymentMethod().name()
            + " order."
            + (StringUtils.hasText(adminNotes) ? " Notes: " + adminNotes : ""));

    paymentRepository.save(payment);
    Order savedOrder = orderRepository.save(order);
    log.info(
        "Admin confirmed {} payment for order {}. Original payment method: {}. Order payment status changed from {} to PAID.",
        paymentMethodConfirmedByAdmin.name(),
        order.getOrderCode(),
        order.getPaymentMethod().name(),
        originalPaymentStatus);
    // Gửi thông báo cho người mua rằng thanh toán đã được xác nhận
    notificationService.sendPaymentSuccessNotification(savedOrder);
    // Gửi thông báo cho người mua rằng trạng thái đơn hàng đã thay đổi
    if (originalOrderStatus != savedOrder.getStatus()) {
      notificationService.sendOrderStatusUpdateNotification(savedOrder, originalOrderStatus);
    }
    //  Gửi thông báo cho Farmer rằng đơn hàng đã được thanh toán và có thể chuẩn bị hàng
    // notificationService.sendOrderPaidNotificationToFarmer(savedOrder);
    return orderMapper.toOrderResponse(savedOrder);
  }

  @Override
  @Transactional(readOnly = true)
  public BankTransferInfoResponse getBankTransferInfoForOrder(
      Long orderId, Authentication authentication) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Order order =
        orderRepository
            .findById(orderId) // Dùng query có fetch
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
    if (!isAdmin && !order.getBuyer().getId().equals(user.getId())) {
      throw new AccessDeniedException(
          "Bạn không có quyền xem thông tin thanh toán cho đơn hàng này.");
    }

    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
        && order.getPaymentMethod() != PaymentMethod.INVOICE) {
      throw new BadRequestException(
          "Thông tin chuyển khoản không áp dụng cho phương thức thanh toán của đơn hàng này ("
              + order.getPaymentMethod()
              + ").");
    }

    // Kiểm tra trạng thái thanh toán phù hợp
    boolean canViewBankInfo = false;
    if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
        && order.getPaymentStatus() == PaymentStatus.PENDING) {
      canViewBankInfo = true;
    } else if (order.getPaymentMethod() == PaymentMethod.INVOICE
        && (order.getPaymentStatus() == PaymentStatus.AWAITING_PAYMENT_TERM
            || order.getPaymentStatus() == PaymentStatus.PENDING)) {
      // Đối với INVOICE, có thể cho xem thông tin CK ngay cả khi đang AWAITING_PAYMENT_TERM
      // hoặc PENDING (cho phép Buyer chủ động trả nợ sớm bằng CK)
      canViewBankInfo = true;
    }

    if (!canViewBankInfo) {
      throw new BadRequestException(
          "Đơn hàng này không ở trạng thái cho phép xem thông tin chuyển khoản (PTTT: "
              + order.getPaymentMethod()
              + ", Trạng thái TT: "
              + order.getPaymentStatus()
              + ").");
    }

    String transferContent = "TT AgriTrade " + order.getOrderCode();
    String qrCodeDataString;

    // Tạo chuỗi cho QR code
    //  Tạo URL đến dịch vụ tạo ảnh QR
    if (StringUtils.hasText(qrServiceUrlBase)
        && StringUtils.hasText(appBankBin)
        && StringUtils.hasText(appBankAccountNumber)) {
      try {
        String encodedOrderInfo =
            URLEncoder.encode(transferContent, StandardCharsets.UTF_8.toString());
        String encodedAccountName =
            URLEncoder.encode(appBankAccountName, StandardCharsets.UTF_8.toString());
        qrCodeDataString =
            String.format(
                "%s/%s-%s-%s.png?amount=%s&addInfo=%s&accountName=%s",
                qrServiceUrlBase.replaceAll("/$", ""), // Xóa dấu / ở cuối nếu có
                appBankBin,
                appBankAccountNumber,
                qrTemplate,
                order.getTotalAmount().toPlainString(),
                encodedOrderInfo,
                encodedAccountName);
        log.info("Generated QR Image URL for order {}: {}", order.getOrderCode(), qrCodeDataString);
      } catch (Exception e) {
        log.error(
            "Error generating QR Image URL for order {}: {}", order.getOrderCode(), e.getMessage());
        qrCodeDataString = "Lỗi tạo mã QR (URL)";
      }
    } else {
      log.warn(
          "QR Service URL Base not configured. Cannot generate QR image URL for order {}",
          order.getOrderCode());
      qrCodeDataString = null;
    }

    return new BankTransferInfoResponse(
        appBankAccountName,
        appBankAccountNumber,
        appBankNameDisplay,
        order.getTotalAmount(),
        order.getOrderCode(),
        transferContent,
        qrCodeDataString);
  }

  // Phương thức helper để điền imageUrls cho sản phẩm trong OrderItems
  private void populateProductImageUrlsInOrder(Order order) {
    if (order != null && order.getOrderItems() != null) {
      for (OrderItem item : order.getOrderItems()) {
        Product product = item.getProduct(); // Lấy Product từ OrderItem
        if (product != null && product.getImages() != null && !product.getImages().isEmpty()) {
          for (ProductImage image : product.getImages()) {
            if (StringUtils.hasText(image.getBlobPath())) {
              image.setImageUrl(fileStorageService.getFileUrl(image.getBlobPath()));
            }
          }
        }
      }
    }
  }

  @Override
  @Transactional // Có thể cần @Transactional nếu  cập nhật trạng thái Order
  public PaymentUrlResponse createPaymentUrl(
      Authentication authentication,
      Long orderId,
      PaymentMethod paymentMethod,
      HttpServletRequest httpServletRequest) {
    User user = SecurityUtils.getCurrentAuthenticatedUser(); // Sử dụng lại helper method
    Order order =
        orderRepository
            .findByIdAndBuyerId(orderId, user.getId()) // Đảm bảo đúng order của user
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    // Kiểm tra trạng thái đơn hàng và thanh toán
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      throw new BadRequestException("Đơn hàng này đã được thanh toán.");
    }
    if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
      throw new BadRequestException("Không thể thanh toán cho đơn hàng đã hủy hoặc đã hoàn thành.");
    }

    PaymentUrlResponse paymentUrlResponse;
    String clientIp = VnPayUtils.getIpAddress(httpServletRequest);
    String frontendReturnUrl;

    switch (paymentMethod) {
      case VNPAY:
        frontendReturnUrl = frontendAppUrl + "/payment/vnpay/result";
        // Cập nhật paymentMethod của Order nếu người dùng chọn lại hoặc thanh toán lại sau khi
        // FAILED
        if (order.getPaymentMethod() != PaymentMethod.VNPAY
            || order.getPaymentStatus() == PaymentStatus.FAILED) {
          order.setPaymentMethod(PaymentMethod.VNPAY);
          order.setPaymentStatus(PaymentStatus.PENDING); // Reset về PENDING

          orderRepository.save(order);
        }
        paymentUrlResponse = vnPayService.createVnPayPaymentUrl(order, clientIp, frontendReturnUrl);
        break;
      case MOMO:
        frontendReturnUrl = frontendAppUrl + "/payment/momo/result";
        String backendIpnUrl = backendAppUrl + "/api/payments/callback/momo/ipn";
        if (order.getPaymentMethod() != PaymentMethod.MOMO
            || order.getPaymentStatus() == PaymentStatus.FAILED) {
          order.setPaymentMethod(PaymentMethod.MOMO);
          order.setPaymentStatus(PaymentStatus.PENDING);
          orderRepository.save(order);
        }
        paymentUrlResponse =
            moMoService.createMoMoPaymentUrl(order, frontendReturnUrl, backendIpnUrl);
        break;
        // Thêm các case cho ZALOPAY, OTHER
      default:
        throw new BadRequestException(
            "Phương thức thanh toán không được hỗ trợ hoặc không hợp lệ cho việc tạo URL: "
                + paymentMethod);
    }

    if (paymentUrlResponse == null || paymentUrlResponse.getPaymentUrl() == null) {
      log.error(
          "Không thể tạo URL thanh toán cho đơn hàng {} với phương thức {}",
          order.getOrderCode(),
          paymentMethod);
      throw new RuntimeException("Không thể tạo URL thanh toán cho " + paymentMethod.name());
    }

    log.info(
        "Tạo URL thanh toán thành công cho đơn hàng {}, phương thức {}: {}",
        order.getOrderCode(),
        paymentMethod,
        paymentUrlResponse.getPaymentUrl());
    return paymentUrlResponse;
  }

  @Override
  @Transactional
  public OrderResponse createAgreedOrder(
      Authentication authentication, AgreedOrderRequest request) {
    // User thực hiện hành động này  là  hoặc Farmer
    User actor = SecurityUtils.getCurrentAuthenticatedUser();

    log.info("User {} is creating an agreed order.", actor.getEmail());

    // **KIỂM TRA VAI TRÒ FARMER**
    if (actor.getRoles().stream().noneMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
      throw new AccessDeniedException("Only farmers can create agreed orders.");
    }

    // **LẤY FARMER ID TỪ CURRENT ACTOR**
    User farmer = actor; // Người đang đăng nhập chính là farmer
    log.info(
        "Farmer {} is creating an agreed order. Buyer ID: {}",
        farmer.getEmail(),
        request.getBuyerId());

    User buyer =
        userRepository
            .findById(request.getBuyerId())
            .orElseThrow(() -> new ResourceNotFoundException("Buyer", "id", request.getBuyerId()));

    // Kiểm tra buyer không phải là chính farmer đó
    if (farmer.getId().equals(buyer.getId())) {
      throw new BadRequestException(
          "Farmer cannot create an agreed order with themselves as the buyer.");
    }

    Order order = new Order();
    order.setOrderCode(generateOrderCode()); // Dùng lại hàm generateOrderCode
    order.setBuyer(buyer);
    order.setFarmer(farmer);
    order.setOrderType(OrderType.B2B); // Hoặc một loại mới như AGREED_DEAL
    order.setStatus(OrderStatus.PENDING); // Hoặc AGREEMENT_PENDING_CONFIRMATION

    // Thông tin giao hàng từ request
    order.setShippingFullName(request.getShippingFullName());
    order.setShippingPhoneNumber(request.getShippingPhoneNumber());
    order.setShippingAddressDetail(request.getShippingAddressDetail());
    order.setShippingProvinceCode(request.getShippingProvinceCode());
    order.setShippingDistrictCode(request.getShippingDistrictCode());
    order.setShippingWardCode(request.getShippingWardCode());

    order.setPaymentMethod(request.getAgreedPaymentMethod());
    // Dựa vào agreedPaymentMethod để set paymentStatus
    if (request.getAgreedPaymentMethod() == PaymentMethod.INVOICE) {
      order.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT_TERM);
    } else {
      order.setPaymentStatus(PaymentStatus.PENDING); // Chờ thanh toán (ví dụ: chuyển khoản)
    }
    order.setNotes(request.getNotes());
    // order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());

    // *** LOGIC  ĐỂ LIÊN KẾT VỚI REQUEST GỐC ***
    if (request.getSourceRequestId() != null) {
      // Tìm SupplyOrderRequest gốc từ ID trong request
      SupplyOrderRequest sourceRequest =
          supplyOrderRequestRepository
              .findById(request.getSourceRequestId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "SupplyOrderRequest", "id", request.getSourceRequestId()));

      //  Kiểm tra xem request này có đúng là của farmer và buyer này không
      if (!sourceRequest.getFarmer().getId().equals(farmer.getId())
          || !sourceRequest.getBuyer().getId().equals(request.getBuyerId())) {
        throw new BadRequestException(
            "The source supply request does not match the order details.");
      }
      // Đảm bảo request đã được chấp nhận và chưa được xử lý ***
      if (sourceRequest.getStatus() != SupplyOrderRequestStatus.FARMER_ACCEPTED) {
        throw new BadRequestException(
            "Cannot create an order from a request that has not been accepted. Current status: "
                + sourceRequest.getStatus());
      }

      // Gán liên kết
      order.setSourceRequest(sourceRequest);
    }

    BigDecimal calculatedSubTotal = BigDecimal.ZERO;
    for (AgreedOrderItemRequest itemRequest : request.getItems()) {
      Product productRef =
          productRepository
              .findById(itemRequest.getProductId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Product reference", "id", itemRequest.getProductId()));

      int requestedQuantityFromForm = itemRequest.getQuantity(); // Số lượng Farmer chốt

      // 1. Lấy đơn vị Farmer đã chốt cho item này
      MassUnit unitFarmerChot = MassUnit.fromString(itemRequest.getUnit());
      if (unitFarmerChot == null) {
        throw new BadRequestException(
            "Đơn vị tính không hợp lệ cho sản phẩm '"
                + productRef.getName()
                + "': "
                + itemRequest.getUnit());
      }

      // 2. Quy đổi số lượng Farmer chốt về KG (hoặc đơn vị cơ sở của sản phẩm)
      // Giả định Product.stockQuantity và Product.unit (đơn vị cơ sở) luôn là KG
      // Nếu Product.unit có thể khác KG, bạn cần thêm bước quy đổi stockQuantity về KG trước khi so
      // sánh.
      // Ở đây, giả sử Product.unit luôn là KG.
      BigDecimal quantityToDeductInKg =
          unitFarmerChot.convertToKg(new BigDecimal(requestedQuantityFromForm));

      // === KIỂM TRA VÀ TRỪ KHO ===
      //  3. Kiểm tra và trừ kho (stockQuantity của Product là số
      // nguyên, lưu theo KG)
      if (productRef.getStatus() != ProductStatus.PUBLISHED || productRef.isDeleted()) {
        throw new BadRequestException("Nguồn cung '" + productRef.getName() + "' không khả dụng.");
      }
      int currentStockKg = productRef.getStockQuantity(); // Đây là số KG trong kho

      if (new BigDecimal(currentStockKg).compareTo(quantityToDeductInKg) < 0) {
        // Chuyển đổi stock hiện tại về đơn vị Farmer chốt để thông báo cho dễ hiểu
        BigDecimal currentStockInFarmerUnit =
            unitFarmerChot.convertFromKg(new BigDecimal(currentStockKg));
        throw new OutOfStockException(
            "Không đủ tồn kho cho '"
                + productRef.getName()
                + "'. Hiện có: "
                + currentStockInFarmerUnit.stripTrailingZeros().toPlainString()
                + " "
                + unitFarmerChot.getDisplayName()
                + ", Yêu cầu: "
                + requestedQuantityFromForm
                + " "
                + unitFarmerChot.getDisplayName(),
            currentStockKg // Trả về tồn kho theo đơn vị cơ sở (KG)
            );
      }
      // Trừ kho (số nguyên KG)
      productRef.setStockQuantity(
          currentStockKg - quantityToDeductInKg.intValue()); // Chuyển BigDecimal về int để trừ
      productRef.setLastStockUpdate(LocalDateTime.now());
      // productRepository.save(productRef); // Sẽ được cascade khi lưu Order

      // 4. Tạo OrderItem
      OrderItem orderItem = new OrderItem();
      orderItem.setProduct(productRef); // Lưu tham chiếu đến sản phẩm gốc
      orderItem.setProductName(itemRequest.getProductName()); // Tên Farmer chốt
      orderItem.setUnit(unitFarmerChot.name()); // Đơn vị Farmer chốt
      orderItem.setQuantity(requestedQuantityFromForm); // Số lượng Farmer chốt
      orderItem.setPricePerUnit(itemRequest.getPricePerUnit());
      BigDecimal itemTotalPrice =
          itemRequest.getPricePerUnit().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
      orderItem.setTotalPrice(itemTotalPrice);
      order.addOrderItem(orderItem);
      calculatedSubTotal = calculatedSubTotal.add(itemTotalPrice);
    }

    order.setSubTotal(calculatedSubTotal);
    // Phí ship và discount có thể là 0 nếu đã gộp vào agreedTotalAmount, hoặc tính riêng nếu cần
    order.setShippingFee(
        request.getAgreedTotalAmount().subtract(calculatedSubTotal).max(BigDecimal.ZERO)); // Ví dụ
    order.setDiscountAmount(BigDecimal.ZERO); // Hoặc giá trị thỏa thuận
    order.setTotalAmount(request.getAgreedTotalAmount());

    Order savedOrder = orderRepository.save(order);

    // Tạo Payment record ban đầu
    createInitialPaymentRecord(savedOrder); // Dùng lại hàm helper đã có

    // Tạo Invoice nếu là phương thức INVOICE
    if (savedOrder.getPaymentMethod() == PaymentMethod.INVOICE) {
      invoiceService.getOrCreateInvoiceForOrder(savedOrder);
    }

    // Cập nhật trạng thái của SupplyOrderRequest gốc thành ACCEPTED sau khi đã tạo Order
    // thành công
    if (order.getSourceRequest() != null) {
      SupplyOrderRequest sourceRequest = order.getSourceRequest();
      sourceRequest.setStatus(SupplyOrderRequestStatus.FARMER_ACCEPTED);
      supplyOrderRequestRepository.save(sourceRequest);
    }

    // Gửi thông báo
    notificationService.sendOrderPlacementNotification(
        savedOrder); // Có thể cần điều chỉnh nội dung thông báo

    log.info("Agreed order {} created by user {}.", savedOrder.getOrderCode(), actor.getEmail());
    // Load lại đầy đủ để trả về
    return orderMapper.toOrderResponse(
        orderRepository.findById(savedOrder.getId()).orElse(savedOrder));
  }

  // Helper mới để quy đổi từ KG sang đơn vị đích (nếu cần cho OutOfStockException message)
  private BigDecimal convertKgToUnit(BigDecimal kgQuantity, MassUnit targetUnit) {
    if (targetUnit == null || kgQuantity == null) return BigDecimal.ZERO;
    return targetUnit.convertFromKg(kgQuantity);
  }

  @Override
  @Transactional
  public void processBuyerPaymentNotification(
      Long orderId, PaymentNotificationRequest request, Authentication authentication) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();
    Order order =
        orderRepository
            .findByIdAndBuyerId(orderId, buyer.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    if (order.getPaymentMethod() != PaymentMethod.INVOICE
        && order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
      throw new BadRequestException(
          "This order does not support payment notification via this method.");
    }
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      log.warn(
          "Buyer {} attempted to notify payment for already PAID order {}",
          buyer.getEmail(),
          order.getOrderCode());
      // Không cần ném lỗi, chỉ log lại
      return;
    }

    log.info(
        "Buyer {} notified payment for order {}. Ref: {}, Notes: {}",
        buyer.getEmail(),
        order.getOrderCode(),
        request.getReferenceCode(),
        request.getNotes());

    // Gửi thông báo cho Admin và Farmer (nếu Farmer quản lý đơn này)
    // notificationService.sendBuyerPaymentNotifiedToAdmin(order, request);
    // notificationService.sendBuyerPaymentNotifiedToFarmer(order, request);
    // )

  }

  @Override
  @Transactional(readOnly = true)
  public List<OrderSummaryResponse> getAllOrdersForAdminExport(
      String keyword,
      OrderStatus status,
      PaymentMethod paymentMethod,
      PaymentStatus paymentStatus,
      Long buyerId,
      Long farmerId,
      OrderType orderType) {
    Specification<Order> spec =
        Specification.where(OrderSpecifications.hasStatus(status))
            .and(OrderSpecifications.byBuyer(buyerId))
            .and(OrderSpecifications.byFarmer(farmerId))
            .and(OrderSpecifications.hasPaymentMethod(paymentMethod))
            .and(OrderSpecifications.hasPaymentStatus(paymentStatus))
            .and(OrderSpecifications.hasOrderType(orderType));

    if (StringUtils.hasText(keyword)) {
      spec =
          spec.and(
              Specification.anyOf(
                  OrderSpecifications.hasOrderCode(keyword),
                  OrderSpecifications.hasBuyerName(keyword),
                  OrderSpecifications.hasFarmerName(keyword)));
    }

    // Sử dụng Sort nhưng không dùng Pageable
    List<Order> orders = orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    return orderMapper.toOrderSummaryResponseList(orders);
  }
}
