package com.yourcompany.agritradels.ordering.service.impl;

import com.yourcompany.agritradels.catalog.domain.Product;
import com.yourcompany.agritradels.catalog.domain.ProductPricingTier;
import com.yourcompany.agritradels.catalog.domain.ProductStatus;
import com.yourcompany.agritradels.catalog.repository.ProductRepository;
import com.yourcompany.agritradels.common.exception.BadRequestException;
import com.yourcompany.agritradels.common.exception.ResourceNotFoundException;
import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.notification.service.EmailService; // Import EmailService
import com.yourcompany.agritradels.notification.service.NotificationService;
import com.yourcompany.agritradels.ordering.domain.*;
import com.yourcompany.agritradels.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritradels.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritradels.ordering.dto.response.OrderResponse;
import com.yourcompany.agritradels.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritradels.ordering.mapper.OrderMapper;
import com.yourcompany.agritradels.ordering.repository.*;
import com.yourcompany.agritradels.ordering.repository.specification.OrderSpecifications; // Import Specifications
import com.yourcompany.agritradels.ordering.service.OrderService;
import com.yourcompany.agritradels.usermanagement.domain.Address;
import com.yourcompany.agritradels.usermanagement.domain.User;
import com.yourcompany.agritradels.usermanagement.repository.AddressRepository; // Import AddressRepository
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException; // Import OptimisticLocking
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException; // Import OptimisticLocking
import org.springframework.retry.annotation.Backoff; // Import Retry
import org.springframework.retry.annotation.Retryable; // Import Retryable
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Import để check Admin
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation; // Import Isolation
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository; // Inject OrderItemRepository
    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;
    private final EmailService emailService; // Inject EmailService
    private final NotificationService notificationService;
    // private final LockProvider lockProvider; // Bỏ qua nếu dùng Optimistic Lock
    private static final String LANG_SON_PROVINCE_CODE = "12";



    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED) // Đảm bảo đọc dữ liệu đã commit
    @Retryable(retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100)) // Thử lại nếu có xung đột optimistic lock
    public List<OrderResponse> checkout(Authentication authentication, CheckoutRequest request) {
        User buyer = getUserFromAuthentication(authentication);
        Address shippingAddress = addressRepository.findByIdAndUserId(request.getShippingAddressId(), buyer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping Address", "id", request.getShippingAddressId()));

        List<CartItem> cartItems = cartItemRepository.findByUserId(buyer.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty.");
        }

        // Phân nhóm item theo Farmer ID
        Map<Long, List<CartItem>> itemsByFarmer = cartItems.stream()
                .collect(Collectors.groupingBy(item -> item.getProduct().getFarmer().getId()));

        List<Order> createdOrders = new ArrayList<>();
        List<Long> processedCartItemIds = new ArrayList<>();

        for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
            Long farmerId = entry.getKey();
            List<CartItem> farmerCartItems = entry.getValue();

            User farmer = userRepository.findById(farmerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Farmer", "id", farmerId));

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

            BigDecimal subTotal = BigDecimal.ZERO;

            for (CartItem cartItem : farmerCartItems) {
                // *** Xử lý Tồn kho với Optimistic Lock ***
                // Lấy product ID và số lượng yêu cầu
                Long productId = cartItem.getProduct().getId();
                int requestedQuantity = cartItem.getQuantity();

                // Tải lại Product trong transaction để lấy version mới nhất (nếu dùng @Version)
                // Hoặc dùng SELECT FOR UPDATE nếu dùng Pessimistic Lock
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId)); // Sản phẩm có thể đã bị xóa

                if (product.getStatus() != ProductStatus.PUBLISHED) {
                    throw new BadRequestException("Product '" + product.getName() + "' is not available for purchase.");
                }

                int currentStock = product.getStockQuantity();
                if (currentStock < requestedQuantity) {
                    throw new BadRequestException("Not enough stock for product: " + product.getName() + ". Available: " + currentStock);
                }

                // Trừ kho
                product.setStockQuantity(currentStock - requestedQuantity);
                // Không cần save product ngay ở đây nếu transaction thành công
                // productRepository.save(product); // Save sẽ được thực hiện cuối transaction

                // Tạo OrderItem
                OrderItem orderItem = new OrderItem();
                orderItem.setProduct(product); // Giữ liên kết
                orderItem.setProductName(product.getName());
                BigDecimal pricePerUnit = determinePrice(product, requestedQuantity, order.getOrderType());
                String unit = determineUnit(product, order.getOrderType());
                orderItem.setUnit(unit);
                orderItem.setPricePerUnit(pricePerUnit);
                orderItem.setQuantity(requestedQuantity);
                BigDecimal itemTotalPrice = pricePerUnit.multiply(BigDecimal.valueOf(requestedQuantity));
                orderItem.setTotalPrice(itemTotalPrice);

                order.addOrderItem(orderItem);
                subTotal = subTotal.add(itemTotalPrice);
                processedCartItemIds.add(cartItem.getId());
            }

            order.setSubTotal(subTotal);
            BigDecimal shippingFee = calculateShippingFee(shippingAddress, farmerCartItems);
            order.setShippingFee(shippingFee);
            BigDecimal discount = calculateDiscount(buyer, farmerCartItems);
            order.setDiscountAmount(discount);
            order.setTotalAmount(subTotal.add(shippingFee).subtract(discount));

            // Lưu Order (bao gồm OrderItems nhờ CascadeType.ALL)
            Order savedOrder = orderRepository.save(order);

            // *** Gửi thông báo đặt hàng thành công ***
            notificationService.sendOrderPlacementNotification(savedOrder);

            // Tạo Payment PENDING
            Payment initialPayment = new Payment();
            initialPayment.setOrder(savedOrder);
            initialPayment.setAmount(savedOrder.getTotalAmount());
            initialPayment.setPaymentGateway(savedOrder.getPaymentMethod().name());
            initialPayment.setStatus(PaymentTransactionStatus.PENDING);
            paymentRepository.save(initialPayment);

            createdOrders.add(savedOrder);
            // Cuối vòng lặp for trong checkout, sau khi save order và payment
            notificationService.sendOrderPlacementNotification(savedOrder);
            log.info("Order {} created successfully for farmer {}", savedOrder.getOrderCode(), farmerId);

            // Gửi email xác nhận (bất đồng bộ)
            // emailService.sendOrderConfirmationEmail(savedOrder); // Cần tạo hàm này trong EmailService
        }

        // Xóa các cart item đã checkout
        if (!processedCartItemIds.isEmpty()) {
            cartItemRepository.deleteAllById(processedCartItemIds);
        }

        // Load lại đầy đủ thông tin để trả về (do save ban đầu có thể chưa flush hết)
        return createdOrders.stream()
                .map(o -> orderRepository.findByIdWithDetails(o.getId()).orElse(o)) // Load lại
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrdersAsBuyer(Authentication authentication, Pageable pageable) {
        User buyer = getUserFromAuthentication(authentication);
        Page<Order> orderPage = orderRepository.findByBuyerIdWithDetails(buyer.getId(), pageable);
        return orderMapper.toOrderSummaryResponsePage(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrdersAsFarmer(Authentication authentication, Pageable pageable) {
        User farmer = getUserFromAuthentication(authentication);
        Page<Order> orderPage = orderRepository.findByFarmerIdWithDetails(farmer.getId(), pageable);
        return orderMapper.toOrderSummaryResponsePage(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getAllOrdersForAdmin(OrderStatus status, Long buyerId, Long farmerId, Pageable pageable) {
        Specification<Order> spec = Specification.where(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.byBuyer(buyerId))
                .and(OrderSpecifications.byFarmer(farmerId));
        Page<Order> orderPage = orderRepository.findAll(spec, pageable); // Dùng findAll với Spec
        return orderMapper.toOrderSummaryResponsePage(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Authentication authentication, Long orderId) {
        User user = getUserFromAuthentication(authentication);
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        boolean isAdmin = authentication.getAuthorities().contains(new SimpleGrantedAuthority(RoleType.ROLE_ADMIN.name()));

        if (!isAdmin && !order.getBuyer().getId().equals(user.getId()) && !order.getFarmer().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not have permission to view this order");
        }
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetailsByCode(Authentication authentication, String orderCode) {
        User user = getUserFromAuthentication(authentication);
        Order order = orderRepository.findByOrderCodeWithDetails(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "code", orderCode));

        boolean isAdmin = authentication.getAuthorities().contains(new SimpleGrantedAuthority(RoleType.ROLE_ADMIN.name()));

        if (!isAdmin && !order.getBuyer().getId().equals(user.getId()) && !order.getFarmer().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not have permission to view this order");
        }
        return orderMapper.toOrderResponse(order);
    }


    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetailsForAdmin(Long orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Authentication authentication, Long orderId, OrderStatusUpdateRequest request) {
        User user = getUserFromAuthentication(authentication);
        Order order = orderRepository.findById(orderId)
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
            throw new BadRequestException("Invalid status transition from " + currentStatus + " to " + newStatus + " by " + (isAdmin ? "Admin" : "Farmer"));
        }

        order.setStatus(newStatus);
        // Cập nhật trạng thái thanh toán nếu giao hàng COD thành công
        if (newStatus == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.PAID);
            // Tạo payment record cho COD nếu cần
            createCodPaymentRecord(order);
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated to {} by user {}", orderId, newStatus, user.getId());
        // Gửi thông báo cập nhật trạng thái
        notificationService.sendOrderStatusUpdateNotification(updatedOrder, currentStatus); // Gọi hàm gửi thông báo

        return getOrderDetailsForAdmin(orderId); // Load lại đầy đủ để trả về
    }

    @Override
    @Transactional
    @Retryable(retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public OrderResponse cancelOrder(Authentication authentication, Long orderId) {
        User user = getUserFromAuthentication(authentication);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_ADMIN);
        // Buyer có thể là Consumer hoặc Business Buyer
        boolean isBuyer = user.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_CONSUMER || r.getName() == RoleType.ROLE_BUSINESS_BUYER);

        // Kiểm tra quyền hủy
        if (!isAdmin && !(isBuyer && order.getBuyer().getId().equals(user.getId()))) {
            throw new AccessDeniedException("User does not have permission to cancel this order");
        }

        // Kiểm tra trạng thái cho phép hủy
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in its current status: " + order.getStatus());
        }

//        // Hoàn trả tồn kho (Cần xử lý cẩn thận race condition)
//        // Load lại OrderItems nếu là LAZY fetch
//        List<OrderItem> itemsToRestore = orderItemRepository.findByOrderId(orderId);
//        for (OrderItem item : itemsToRestore) {
//            Product product = productRepository.findById(item.getProduct().getId())
//                    .orElse(null); // Lấy lại product mới nhất
//            if (product != null) {
//                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
//                productRepository.save(product); // Save lại product
//            } else {
//                log.warn("Product with id {} not found while trying to restore stock for cancelled order {}", item.getProduct().getId(), orderId);
//            }
//        }

        // *** Hoàn trả tồn kho (Cải thiện với Optimistic Lock) ***
        List<OrderItem> itemsToRestore = orderItemRepository.findByOrderId(orderId); // Lấy các item của đơn hàng
        for (OrderItem item : itemsToRestore) {
            // Lấy product ID và số lượng cần hoàn trả
            Long productId = item.getProduct().getId();
            int quantityToRestore = item.getQuantity();

            // Tải lại Product trong transaction để lấy version mới nhất
            Product product = productRepository.findById(productId)
                    .orElse(null); // Có thể sản phẩm đã bị xóa vật lý?

            if (product != null) {
                log.debug("Restoring stock for product {}: current={}, restoring={}", productId, product.getStockQuantity(), quantityToRestore);
                product.setStockQuantity(product.getStockQuantity() + quantityToRestore);
                try {
                    productRepository.saveAndFlush(product); // Save và flush ngay để phát hiện xung đột sớm (nếu có)
                } catch (OptimisticLockingFailureException e) {
                    log.warn("Optimistic lock failed while restoring stock for product {} in order {}. Retrying...", productId, orderId);
                    throw e; // Ném lại exception để @Retryable bắt và thử lại
                } catch (Exception e) {
                    log.error("Error saving product {} while restoring stock for order {}", productId, orderId, e);
                    // Quyết định xử lý tiếp hay dừng lại? Có thể throw lỗi nghiêm trọng hơn.
                    throw new RuntimeException("Failed to restore stock for product " + productId, e);
                }
            } else {
                log.warn("Product with id {} not found while trying to restore stock for cancelled order {}", productId, orderId);
                // Cần quyết định: Bỏ qua hay báo lỗi?
            }
        }


        order.setStatus(OrderStatus.CANCELLED);
        // Cập nhật trạng thái thanh toán
        if(order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            // TODO: Xử lý hoàn tiền thực tế
            log.info("Order {} cancelled, payment status set to REFUNDED. Manual refund may be required.", orderId);
        } else if (order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.AWAITING_PAYMENT_TERM) {
            order.setPaymentStatus(PaymentStatus.FAILED); // Hoặc một trạng thái hủy khác
        }

//        Order cancelledOrder = orderRepository.save(order);
        orderRepository.save(order); // Lưu trạng thái cuối cùng của Order
        log.info("Order {} cancelled by user {}", orderId, user.getId());
//        // TODO: Gửi thông báo hủy đơn
//
//        return getOrderDetailsForAdmin(orderId); // Load lại đầy đủ
        // Gửi thông báo hủy đơn
        notificationService.sendOrderCancellationNotification(order); // Gọi hàm gửi thông báo

        // Load lại đầy đủ thông tin và map để trả về
        Order cancelledOrderFull = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        return orderMapper.toOrderResponse(cancelledOrderFull);
    }

    // --- Helper Methods ---
    private User getUserFromAuthentication(Authentication authentication) {
        // 1. Kiểm tra xem authentication có hợp lệ không
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            log.warn("Attempted to get user from null or unauthenticated Authentication object.");
            throw new AccessDeniedException("User is not authenticated or authentication details are missing.");
        }

        // 2. Lấy principal (thông tin định danh chính)
        Object principal = authentication.getPrincipal();
        String userIdentifier; // Sẽ lưu email hoặc username

        // Kiểm tra kiểu của principal
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            // Nếu principal là UserDetails (trường hợp phổ biến khi dùng UserDetailsService)
            userIdentifier = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            // Nếu principal chỉ là String (ví dụ: khi lấy từ JWT Filter mà không load lại UserDetails)
            userIdentifier = (String) principal;
            // Kiểm tra xem có phải là "anonymousUser" không
            if ("anonymousUser".equals(userIdentifier)) {
                log.warn("Attempted operation by anonymous user.");
                throw new AccessDeniedException("Anonymous user cannot perform this action.");
            }
        } else {
            // Trường hợp principal không xác định được
            log.error("Unexpected principal type: {}", principal.getClass().getName());
            throw new AccessDeniedException("Cannot identify user from authentication principal.");
        }

        // 3. Tìm User trong database bằng email (hoặc username)
        // findByEmail đã tự động lọc is_deleted=false nhờ @Where trên User entity
        return userRepository.findByEmail(userIdentifier)
                .orElseThrow(() -> {
                    // Lỗi này không nên xảy ra nếu JWT hợp lệ và user chưa bị xóa/inactive sau khi login
                    log.error("Authenticated user not found in database with identifier: {}", userIdentifier);
                    return new UsernameNotFoundException("Authenticated user not found: " + userIdentifier);
                });
    }


    private String generateOrderCode() {
        // Ví dụ: LS + Năm + Tháng + Ngày + 4 số ngẫu nhiên
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "LS" + datePart + "-" + randomPart;
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
        // Logic xác định B2B/B2C, ví dụ dựa trên vai trò người mua
        boolean isBusiness = buyer.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_BUSINESS_BUYER);
        // Hoặc dựa trên số lượng/loại sản phẩm trong giỏ hàng
        return isBusiness ? OrderType.B2B : OrderType.B2C;
    }

    private BigDecimal determinePrice(Product product, int quantity, OrderType type) {
        // Logic xác định giá B2C/B2B, có thể dựa vào bậc giá
        if (type == OrderType.B2B && product.isB2bAvailable()) {
            // Tìm bậc giá phù hợp (nếu dùng pricing tiers)
            BigDecimal tieredPrice = product.getPricingTiers().stream()
                    .filter(tier -> quantity >= tier.getMinQuantity())
                    .max(Comparator.comparing(ProductPricingTier::getMinQuantity)) // Lấy bậc cao nhất đạt được
                    .map(ProductPricingTier::getPricePerUnit)
                    .orElse(product.getB2bBasePrice()); // Nếu không có bậc nào thì dùng giá base

            if (tieredPrice != null) {
                return tieredPrice;
            }
        }
        // Mặc định trả về giá B2C
        return product.getPrice();
    }

    private String determineUnit(Product product, OrderType type) {
        if (type == OrderType.B2B && product.isB2bAvailable() && StringUtils.hasText(product.getB2bUnit())) {
            return product.getB2bUnit();
        }
        return product.getUnit();
    }

    private BigDecimal calculateShippingFee(Address address, List<CartItem> items) {
        // Ví dụ: Phí cố định cho tất cả đơn hàng trong tỉnh Lạng Sơn
        final BigDecimal LANG_SON_SHIPPING_FEE = new BigDecimal("15000.00"); // 15k
        // Phí cố định cho các tỉnh khác (nếu B2C ra ngoài tỉnh)
        final BigDecimal OTHER_PROVINCE_SHIPPING_FEE = new BigDecimal("30000.00"); // 30k

        if (LANG_SON_PROVINCE_CODE.equals(address.getProvinceCode())) {
            return LANG_SON_SHIPPING_FEE;
        } else {
            // Áp dụng nếu B2C cho phép giao ngoài tỉnh
            // return OTHER_PROVINCE_SHIPPING_FEE;
            // Hoặc ném lỗi nếu chỉ giao trong tỉnh
            log.warn("Attempted to calculate shipping fee for address outside Lang Son: {}", address.getProvinceCode());
            // Tạm thời trả về phí Lạng Sơn hoặc 0, hoặc throw lỗi tùy logic checkout
            return LANG_SON_SHIPPING_FEE; // Hoặc BigDecimal.ZERO;
        }
        // Logic phức tạp hơn có thể dựa vào trọng lượng (cần thêm vào Product), khoảng cách...
    }

    private BigDecimal calculateDiscount(User buyer, List<CartItem> items) {
        // TODO: Implement logic tính giảm giá (voucher, khuyến mãi...)
        return BigDecimal.ZERO;
    }

    private boolean isValidStatusTransition(OrderStatus current, OrderStatus next, boolean isAdmin, boolean isFarmer) {
        if (current == next) return false; // Không được cập nhật thành chính nó
        if (current == OrderStatus.DELIVERED || current == OrderStatus.CANCELLED || current == OrderStatus.RETURNED) {
             // Không đổi trạng thái từ các trạng thái cuối cùng
            log.warn("Attempt to change status from final state: {} to {}", current, next);
            return false;
        }

        switch (current) {
            case PENDING:
                // Từ PENDING: Admin/Farmer có thể CONFIRMED, Admin/Buyer có thể CANCELLED (Buyer gọi API riêng)
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
                // Có thể thêm trạng thái RETURNED nếu cần
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
}