package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductPricingTier;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.notification.service.EmailService; // Import EmailService
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.*;
import com.yourcompany.agritrade.ordering.repository.specification.OrderSpecifications; // Import Specifications
import com.yourcompany.agritrade.ordering.service.OrderService;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository; // Import AddressRepository
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;

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
import java.math.RoundingMode;
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
    private final FarmerProfileRepository farmerProfileRepository;
    // private final LockProvider lockProvider; // Bỏ qua nếu dùng Optimistic Lock
    private static final String LANG_SON_PROVINCE_CODE = "20";



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

            // *** Lấy Farmer Profile để biết tỉnh của Farmer ***
            FarmerProfile farmerProfile = farmerProfileRepository.findById(farmerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Farmer Profile", "userId", farmerId));

            Order order = new Order();
            order.setBuyer(buyer);
            order.setFarmer(farmer);
            order.setOrderType(determineOrderType(buyer, farmerCartItems)); // Xác định B2B/B2C
            order.setOrderCode(generateOrderCode());
            order.setPaymentMethod(request.getPaymentMethod());
            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setStatus(OrderStatus.PENDING);
            order.setNotes(request.getNotes());
            // *** GÁN PO NUMBER NẾU LÀ ĐƠN B2B ***
            if (order.getOrderType() == OrderType.B2B) {
                order.setPurchaseOrderNumber(request.getPurchaseOrderNumber());
            }
            // ************************************
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
                    throw new OutOfStockException(
                            "Not enough stock for product: " + product.getName() + ". Available: " + currentStock,
                            currentStock // Truyền số lượng tồn thực tế

                    );
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
//            BigDecimal shippingFee = calculateShippingFee(shippingAddress, farmerCartItems);
//            order.setShippingFee(shippingFee);
//            BigDecimal discount = calculateDiscount(buyer, farmerCartItems);
//            order.setDiscountAmount(discount);
//            order.setTotalAmount(subTotal.add(shippingFee).subtract(discount));
            // *** GỌI HÀM TÍNH PHÍ SHIP VÀ DISCOUNT ĐÃ CẬP NHẬT ***
            BigDecimal shippingFee = calculateShippingFee(shippingAddress, farmerProfile, farmerCartItems, order.getOrderType()); // Truyền thêm OrderType
            order.setShippingFee(shippingFee);
            BigDecimal discount = calculateDiscount(buyer, subTotal); // Truyền subTotal
            order.setDiscountAmount(discount);
            order.setTotalAmount(subTotal.add(shippingFee).subtract(discount));
            // ****************************************************

            // Lưu Order (bao gồm OrderItems nhờ CascadeType.ALL)
            Order savedOrder = orderRepository.save(order);

            // *** Gửi thông báo đặt hàng thành công ***
            notificationService.sendOrderPlacementNotification(savedOrder);

            createInitialPaymentRecord(savedOrder); // Helper tạo paymentcalculateShippingFee

            // Tạo Payment PENDING
//            Payment initialPayment = new Payment();
//            initialPayment.setOrder(savedOrder);
//            initialPayment.setAmount(savedOrder.getTotalAmount());
//            initialPayment.setPaymentGateway(savedOrder.getPaymentMethod().name());
//            initialPayment.setStatus(PaymentTransactionStatus.PENDING);
//            paymentRepository.save(initialPayment);

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
        if (type == OrderType.B2B && product.isB2bEnabled()) {
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
        if (type == OrderType.B2B && product.isB2bEnabled() && StringUtils.hasText(product.getB2bUnit())) {
            return product.getB2bUnit();
        }
        return product.getUnit();
    }

    private BigDecimal calculateShippingFee(Address shippingAddress, FarmerProfile farmerProfile, List<CartItem> items, OrderType orderType){
        if (items == null || items.isEmpty() || shippingAddress == null || farmerProfile == null) {
            log.warn("Cannot calculate shipping fee due to missing input: shippingAddress={}, farmerProfile={}, itemsEmpty={}",
                    shippingAddress == null, farmerProfile == null, items == null || items.isEmpty());
            return BigDecimal.ZERO; // Hoặc ném lỗi nếu các thông tin này là bắt buộc
        }

        // --- Lấy mã tỉnh của người bán và người nhận ---
        String buyerProvinceCode = shippingAddress.getProvinceCode();
        String farmerProvinceCode = farmerProfile.getProvinceCode();

        if (buyerProvinceCode == null || farmerProvinceCode == null) {
            log.error("Cannot calculate shipping fee: Missing province code. Buyer: {}, Farmer: {}", buyerProvinceCode, farmerProvinceCode);
            throw new BadRequestException("Không thể xác định tỉnh/thành phố của người bán hoặc người nhận.");
        }

        // --- Xác định giao nội tỉnh hay ngoại tỉnh ---
        boolean isIntraProvinceShipment = buyerProvinceCode.equals(farmerProvinceCode);

        // --- Định nghĩa các mức phí (Ví dụ - bạn cần điều chỉnh theo thực tế) ---
        final BigDecimal INTRA_PROVINCE_FEE = new BigDecimal("15000.00"); // Phí giao nội tỉnh (B2C & B2B)
        final BigDecimal INTER_PROVINCE_FEE = new BigDecimal("30000.00"); // Phí giao ngoại tỉnh (Chỉ B2C)
        // (Tùy chọn) Có thể thêm phí theo trọng lượng/kích thước nếu cần
         final BigDecimal FEE_PER_KG_INTRA = new BigDecimal("3000.00");
         final BigDecimal FEE_PER_KG_INTER = new BigDecimal("5000.00");


        // --- Xử lý B2B ---
        if (orderType == OrderType.B2B) {
            if (!isIntraProvinceShipment) {
                // Nếu B2B mà khác tỉnh -> không cho phép
                log.warn("B2B order rejected: Shipment between different provinces. Buyer Province: {}, Farmer Province: {}", buyerProvinceCode, farmerProvinceCode);
                throw new BadRequestException("Đơn hàng doanh nghiệp chỉ hỗ trợ giao hàng trong cùng tỉnh với nông dân.");
            } else {
                // B2B nội tỉnh -> Áp dụng phí nội tỉnh
                log.debug("Calculating B2B intra-province shipping fee for order type {}", orderType);
                // double totalWeightKg = calculateTotalWeight(items); // Tính trọng lượng nếu cần
                // BigDecimal weightFee = FEE_PER_KG_INTRA.multiply(BigDecimal.valueOf(totalWeightKg)).setScale(0, RoundingMode.CEILING);
                // return INTRA_PROVINCE_FEE.add(weightFee).max(BigDecimal.ZERO);
                return INTRA_PROVINCE_FEE; // Trả về phí B2B nội tỉnh
            }
        }

        // --- Xử lý B2C ---
        if (isIntraProvinceShipment) {
            // B2C nội tỉnh
            log.debug("Calculating B2C intra-province shipping fee for order type {}", orderType);
            // double totalWeightKg = calculateTotalWeight(items); // Tính trọng lượng nếu cần
            // BigDecimal weightFee = FEE_PER_KG_INTRA.multiply(BigDecimal.valueOf(totalWeightKg)).setScale(0, RoundingMode.CEILING);
            // return INTRA_PROVINCE_FEE.add(weightFee).max(BigDecimal.ZERO);
            return INTRA_PROVINCE_FEE; // Trả về phí B2C nội tỉnh
        } else {
            // B2C ngoại tỉnh
            log.debug("Calculating B2C inter-province shipping fee for order type {}", orderType);
            // double totalWeightKg = calculateTotalWeight(items); // Tính trọng lượng nếu cần
            // BigDecimal weightFee = FEE_PER_KG_INTER.multiply(BigDecimal.valueOf(totalWeightKg)).setScale(0, RoundingMode.CEILING);
            // return INTER_PROVINCE_FEE.add(weightFee).max(BigDecimal.ZERO);
            return INTER_PROVINCE_FEE; // Trả về phí B2C ngoại tỉnh
        }
    }

    // (Tùy chọn) Tách hàm tính tổng trọng lượng
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

    // (Tùy chọn) Tách hàm tính subTotal
    private BigDecimal calculateSubTotal(List<CartItem> items, OrderType orderType) {
        BigDecimal subTotal = BigDecimal.ZERO;
        for (CartItem cartItem : items) {
            Product product = cartItem.getProduct(); // Cần đảm bảo product được load
            if(product == null) continue; // Bỏ qua nếu product null
            int requestedQuantity = cartItem.getQuantity();
            BigDecimal pricePerUnit = determinePrice(product, requestedQuantity, orderType);
            BigDecimal itemTotalPrice = pricePerUnit.multiply(BigDecimal.valueOf(requestedQuantity));
            subTotal = subTotal.add(itemTotalPrice);
        }
        return subTotal;
    }




    private BigDecimal calculateDiscount(User buyer, BigDecimal subTotal /*, String voucherCode - nếu dùng */) { // Chữ ký mới

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

        boolean isBusiness = buyer.getRoles().stream().anyMatch(r -> r.getName() == RoleType.ROLE_BUSINESS_BUYER);

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

        // --- Xử lý Voucher (Ví dụ cơ bản) ---
        /*
        if (StringUtils.hasText(voucherCode)) {
            Optional<Voucher> voucherOpt = voucherRepository.findByCodeAndIsActiveTrue(voucherCode);
            if (voucherOpt.isPresent()) {
                Voucher voucher = voucherOpt.get();
                // Kiểm tra điều kiện áp dụng voucher (ngày hết hạn, giá trị đơn tối thiểu, số lượt dùng...)
                if (isValidVoucher(voucher, buyer, subTotal)) {
                    if (voucher.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                        fixedDiscount = voucher.getDiscountValue();
                    } else if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                        BigDecimal calculatedPercentDiscount = subTotal.multiply(voucher.getDiscountValue().divide(BigDecimal.valueOf(100)));
                        // Áp dụng mức giảm tối đa nếu có
                        if (voucher.getMaxDiscountAmount() != null && calculatedPercentDiscount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                            fixedDiscount = voucher.getMaxDiscountAmount();
                        } else {
                            fixedDiscount = calculatedPercentDiscount;
                        }
                    }
                    // TODO: Tăng số lượt đã sử dụng voucher
                    // voucher.setCurrentUsage(voucher.getCurrentUsage() + 1);
                    // voucherRepository.save(voucher);
                    log.info("Applied voucher {} for user {}", voucherCode, buyer.getId());
                } else {
                    log.warn("Voucher {} is not valid for this order/user.", voucherCode);
                    // Có thể ném lỗi hoặc chỉ bỏ qua voucher
                    // throw new BadRequestException("Mã giảm giá không hợp lệ hoặc không đủ điều kiện áp dụng.");
                }
            } else {
                 log.warn("Voucher code {} not found.", voucherCode);
                 // throw new BadRequestException("Mã giảm giá không tồn tại.");
            }
        }
        */

        // Kết hợp các loại giảm giá (ví dụ: lấy giá trị lớn hơn)
        discount = percentageDiscount.max(fixedDiscount);

        // Làm tròn tiền giảm giá (ví dụ: làm tròn đến đơn vị nghìn đồng)
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            discount = discount.setScale(0, RoundingMode.DOWN); // Làm tròn xuống đơn vị đồng
            log.info("Applied discount of {} for order subtotal {}", discount, subTotal);
        }


        // TODO: Thêm logic xử lý voucher code nếu có
        // String voucherCode = request.getVoucherCode(); // Cần thêm vào CheckoutRequest
        // if (StringUtils.hasText(voucherCode)) {
        //    BigDecimal voucherDiscount = applyVoucher(voucherCode, subTotal, buyer);
        //    discount = discount.add(voucherDiscount); // Cộng dồn hoặc lấy max tùy chính sách
        // }

        return discount;

        // Helper kiểm tra voucher (cần hoàn thiện)
     /*
     private boolean isValidVoucher(Voucher voucher, User buyer, BigDecimal subTotal) {
         // Kiểm tra ngày hiệu lực
         // Kiểm tra giá trị đơn tối thiểu
         // Kiểm tra số lượt sử dụng còn lại
         // Kiểm tra xem voucher có áp dụng cho user/nhóm user này không
         return true; // Tạm thời
     }
     */

        // Cập nhật lời gọi trong hàm checkout:
        // BigDecimal discount = calculateDiscount(buyer, subTotal /*, request.getVoucherCode()*/);
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

    // Helper tạo Payment Record ban đầu
    private void createInitialPaymentRecord(Order order) {
        Payment initialPayment = new Payment();
        initialPayment.setOrder(order);
        initialPayment.setAmount(order.getTotalAmount());
        initialPayment.setPaymentGateway(order.getPaymentMethod().name());
        // Đặt trạng thái ban đầu phù hợp
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            initialPayment.setStatus(PaymentTransactionStatus.PENDING); // Chờ thanh toán khi nhận hàng
        } else if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER || order.getPaymentMethod() == PaymentMethod.INVOICE) {
            initialPayment.setStatus(PaymentTransactionStatus.PENDING); // Chờ xác nhận chuyển khoản/thanh toán công nợ
            // Có thể set paymentStatus của Order là AWAITING_PAYMENT_TERM cho INVOICE
        } else { // Các cổng online khác
            initialPayment.setStatus(PaymentTransactionStatus.PENDING); // Chờ callback từ cổng thanh toán
        }
        paymentRepository.save(initialPayment);
    }
}