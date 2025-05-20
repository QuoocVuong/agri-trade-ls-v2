package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductPricingTier;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.notification.service.EmailService; // Import EmailService
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderCalculationRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.BankTransferInfoResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderCalculationResponse;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private final FileStorageService fileStorageService;
    // private final LockProvider lockProvider; // Bỏ qua nếu dùng Optimistic Lock
    private static final String LANG_SON_PROVINCE_CODE = "20";

    @Value("${app.bank.accountName}") private String appBankAccountName;
    @Value("${app.bank.accountNumber}") private String appBankAccountNumber;
    @Value("${app.bank.nameDisplay}") private String appBankNameDisplay;
    @Value("${app.bank.bin}") private String appBankBin; // Mã BIN ngân hàng
    @Value("${app.bank.qr.serviceUrlBase:#{null}}") private String qrServiceUrlBase; // Ví dụ: https://img.vietqr.io/image
    @Value("${app.bank.qr.template:compact2}") private String qrTemplate;


    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED) // Đảm bảo đọc dữ liệu đã commit
    @Retryable(retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100))
    // Thử lại nếu có xung đột optimistic lock
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
                .collect(Collectors.groupingBy(item -> {
                    // Thêm kiểm tra null cho product ở đây để an toàn hơn
                    Product p = item.getProduct();
                    if (p == null || p.getFarmer() == null) {
                        log.error("CartItem ID {} has null product or farmer. Skipping.", item.getId());
                        // Có thể ném lỗi hoặc xử lý khác
                        throw new IllegalStateException("Invalid cart item found with missing product or farmer data.");
                    }
                    return p.getFarmer().getId();
                }));

        List<Order> createdOrders = new ArrayList<>();
        List<Long> processedCartItemIds = new ArrayList<>();
        List<String> unavailableProductMessages = new ArrayList<>(); // Lưu thông báo lỗi

        for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
            Long farmerId = entry.getKey();
            List<CartItem> farmerCartItems = entry.getValue();

            User farmer = userRepository.findById(farmerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Farmer", "id", farmerId));

            if (farmer == null) { // Kiểm tra farmer tồn tại
                log.error("Farmer with ID {} not found for items in cart. Skipping this farmer's items.", farmerId);
                // Thêm các cart item ID này vào danh sách để xóa sau
                farmerCartItems.forEach(ci -> processedCartItemIds.add(ci.getId()));
                unavailableProductMessages.add("Một số sản phẩm từ người bán không xác định đã bị xóa khỏi giỏ hàng.");
                continue; // Bỏ qua các sản phẩm của farmer này
            }

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
            boolean farmerOrderHasValidItems = false; // Cờ kiểm tra xem farmer này có item hợp lệ không

            for (CartItem cartItem : farmerCartItems) {
                // *** Xử lý Tồn kho với Optimistic Lock ***
                // Lấy product ID và số lượng yêu cầu
                Long productId = cartItem.getProduct() != null ? cartItem.getProduct().getId() : null;
                if (productId == null) {
                    log.warn("CartItem ID {} has null product. Skipping.", cartItem.getId());
                    processedCartItemIds.add(cartItem.getId()); // Đánh dấu để xóa
                    continue;
                }
                int requestedQuantity = cartItem.getQuantity();

                // Tải lại Product trong transaction để lấy version mới nhất (nếu dùng @Version)
                // Hoặc dùng SELECT FOR UPDATE nếu dùng Pessimistic Lock
                Product product;
                try {
                    // Tải lại Product để kiểm tra tồn kho và trạng thái
                    product = productRepository.findById(productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

                    if (product.getStatus() != ProductStatus.PUBLISHED || product.isDeleted()) { // Kiểm tra cả isDeleted ở đây
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
                    processedCartItemIds.add(cartItem.getId());// Đánh dấu cart item đã xử lý
                    farmerOrderHasValidItems = true; // Đánh dấu farmer này có item hợp lệ


                } catch (ResourceNotFoundException | BadRequestException | OutOfStockException e) {
                        // Nếu sản phẩm không tìm thấy, không hợp lệ, hoặc hết hàng
                        log.warn("Cannot process cart item ID {} for product ID {}: {}", cartItem.getId(), productId, e.getMessage());
                        processedCartItemIds.add(cartItem.getId()); // Đánh dấu để xóa khỏi giỏ hàng
                        unavailableProductMessages.add("Sản phẩm '" + (cartItem.getProduct() != null ? cartItem.getProduct().getName() : "ID "+productId) + "' không còn khả dụng và đã bị xóa khỏi giỏ hàng.");
                        // Không ném lỗi ra ngoài ngay, tiếp tục xử lý các item khác
                    } catch (OptimisticLockingFailureException e) {
                        log.warn("Optimistic lock failed during checkout for product ID {}. Retrying...", productId);
                        throw e; // Ném lại để @Retryable xử lý
                    }
                } // Kết thúc vòng lặp qua cart items của farmer

            // Chỉ tạo order cho farmer nếu có ít nhất 1 item hợp lệ
            if (farmerOrderHasValidItems) {

            order.setSubTotal(subTotal);
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
            } else {
                log.info("No valid items found for farmer {}. Skipping order creation for this farmer.", farmerId);
            }
        } // Kết thúc vòng lặp qua các farmer

        // Xóa các cart item đã checkout
        if (!processedCartItemIds.isEmpty()) {
            cartItemRepository.deleteAllById(processedCartItemIds);
        }
        // Nếu có lỗi về sản phẩm không khả dụng, ném lỗi tổng hợp
        if (!unavailableProductMessages.isEmpty()) {
            throw new BadRequestException(String.join("\n", unavailableProductMessages));
        }
        // Nếu không có đơn hàng nào được tạo (do tất cả sản phẩm đều lỗi)
        if (createdOrders.isEmpty()) {
            throw new BadRequestException("Không thể tạo đơn hàng do tất cả sản phẩm trong giỏ không hợp lệ.");
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
    public Page<OrderSummaryResponse> getMyOrdersAsFarmer(Authentication authentication, String keyword, OrderStatus status, Pageable pageable) {
        User farmer = getUserFromAuthentication(authentication);
//        Page<Order> orderPage = orderRepository.findByFarmerIdWithDetails(farmer.getId(), pageable);
//        return orderMapper.toOrderSummaryResponsePage(orderPage);
//    }
        Specification<Order> spec = Specification.where(OrderSpecifications.byFarmer(farmer.getId())); // Luôn lọc theo farmerId

        if (StringUtils.hasText(keyword)) {
            // Giả sử keyword có thể là mã đơn hàng hoặc tên người mua
            spec = spec.and(
                    Specification.anyOf( // Sử dụng OR
                            OrderSpecifications.hasOrderCode(keyword), // Cần tạo spec này
                            OrderSpecifications.hasBuyerName(keyword)  // Cần tạo spec này
                    )
            );
        }
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status)); // Dùng lại spec đã có
        }

        // Fetch các thông tin cần thiết cho OrderSummaryResponse để tránh N+1
        // Ví dụ: fetch buyer, farmer (nếu OrderSummaryResponse cần tên của họ)
        // spec = spec.and(OrderSpecifications.fetchBuyerAndFarmerSummary()); // Cần tạo spec này

        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        // QUAN TRỌNG: Gọi populateProductImageUrlsInOrder nếu OrderSummaryResponse
        // hoặc bất kỳ DTO con nào của nó cần imageUrl từ ProductImage
        // Nếu OrderSummaryResponse không hiển thị ảnh sản phẩm thì không cần dòng này.
        // orderPage.getContent().forEach(this::populateProductImageUrlsInOrder);


        return orderPage.map(orderMapper::toOrderSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getAllOrdersForAdmin(String keyword, OrderStatus status, Long buyerId, Long farmerId, Pageable pageable) {
        Specification<Order> spec = Specification.where(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.byBuyer(buyerId))
                .and(OrderSpecifications.byFarmer(farmerId));

        if (StringUtils.hasText(keyword)) {
            // Keyword có thể là mã đơn hàng, tên người mua, hoặc tên người bán
            spec = spec.and(
                    Specification.anyOf(
                            OrderSpecifications.hasOrderCode(keyword),
                            OrderSpecifications.hasBuyerName(keyword),
                            OrderSpecifications.hasFarmerName(keyword) // Cần tạo spec này
                    )
            );
        }

        // Fetch thông tin cần thiết cho OrderSummaryResponse
        // spec = spec.and(OrderSpecifications.fetchBuyerAndFarmerSummary()); // Đã có trong OrderRepository
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

        populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP

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
        populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP
        return orderMapper.toOrderResponse(order);
    }


    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetailsForAdmin(Long orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        populateProductImageUrlsInOrder(order); // << GỌI HELPER TRƯỚC KHI MAP
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
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
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

    private BigDecimal calculateShippingFee(Address shippingAddress, FarmerProfile farmerProfile, List<CartItem> items, OrderType orderType) {
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
            if (product == null) continue; // Bỏ qua nếu product null
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
        // ****** ĐẶT TRẠNG THÁI THANH TOÁN BAN ĐẦU ******
        PaymentTransactionStatus initialStatus;
        PaymentStatus orderPaymentStatus = PaymentStatus.PENDING; // Mặc định

        switch (order.getPaymentMethod()) {
            case COD:
            case BANK_TRANSFER: // Chuyển khoản cũng cần xác nhận nên là PENDING ban đầu
                initialStatus = PaymentTransactionStatus.PENDING;
                orderPaymentStatus = PaymentStatus.PENDING;
                break;
            case INVOICE: // Nếu dùng công nợ
                initialStatus = PaymentTransactionStatus.PENDING; // Giao dịch payment có thể vẫn là pending
                orderPaymentStatus = PaymentStatus.AWAITING_PAYMENT_TERM; // Nhưng trạng thái đơn hàng là chờ công nợ
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
        order.setPaymentStatus(orderPaymentStatus); // <<< CẬP NHẬT TRẠNG THÁI THANH TOÁN CỦA ORDER
        // ***********************************************

        paymentRepository.save(initialPayment);
    }

    @Override
    @Transactional(readOnly = true) // Chỉ đọc, không thay đổi dữ liệu
    public OrderCalculationResponse calculateOrderTotals(Authentication authentication, OrderCalculationRequest request) {
        User buyer = getUserFromAuthentication(authentication);
        List<CartItem> cartItems = cartItemRepository.findByUserId(buyer.getId());
        if (cartItems.isEmpty()) {
            // Trả về giá trị 0 nếu giỏ hàng trống
            return new OrderCalculationResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, OrderType.B2C);
        }

        Address shippingAddress = null;
        if (request != null && request.getShippingAddressId() != null) {
            shippingAddress = addressRepository.findByIdAndUserId(request.getShippingAddressId(), buyer.getId())
                    .orElse(null); // Lấy địa chỉ nếu có ID, không thì bỏ qua (phí ship sẽ là mặc định)
        }
        // Nếu không có addressId, có thể lấy địa chỉ mặc định của user
        if (shippingAddress == null) {
            shippingAddress = addressRepository.findByUserIdAndIsDefaultTrue(buyer.getId()).orElse(null);
        }


        // Phân nhóm theo farmer (cần farmer profile để tính ship)
        Map<Long, List<CartItem>> itemsByFarmer = cartItems.stream()
                .collect(Collectors.groupingBy(item -> item.getProduct().getFarmer().getId()));

        BigDecimal totalSubTotal = BigDecimal.ZERO;
        BigDecimal totalShippingFee = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        OrderType finalOrderType = OrderType.B2C; // Mặc định là B2C

        for (Map.Entry<Long, List<CartItem>> entry : itemsByFarmer.entrySet()) {
            Long farmerId = entry.getKey();
            List<CartItem> farmerCartItems = entry.getValue();
            FarmerProfile farmerProfile = farmerProfileRepository.findById(farmerId).orElse(null); // Lấy profile

            // Xác định OrderType (chỉ cần làm 1 lần dựa trên buyer)
            finalOrderType = determineOrderType(buyer, farmerCartItems); // Giả sử hàm này chỉ dựa vào buyer

            BigDecimal farmerSubTotal = BigDecimal.ZERO;
            for (CartItem cartItem : farmerCartItems) {
                Product product = cartItem.getProduct(); // Giả sử đã được fetch EAGER hoặc LAZY load ok
                if (product == null) continue;
                int quantity = cartItem.getQuantity();
                BigDecimal price = determinePrice(product, quantity, finalOrderType);
                farmerSubTotal = farmerSubTotal.add(price.multiply(BigDecimal.valueOf(quantity)));
            }

            // Tính phí ship cho farmer này (cần địa chỉ người nhận và profile farmer)
            BigDecimal farmerShippingFee = BigDecimal.ZERO;
            if (shippingAddress != null && farmerProfile != null) {
                farmerShippingFee = calculateShippingFee(shippingAddress, farmerProfile, farmerCartItems, finalOrderType);
            } else {
                log.warn("Cannot calculate shipping fee for farmer {} due to missing address or profile", farmerId);
                // Có thể đặt phí mặc định hoặc báo lỗi tùy logic
            }


            totalSubTotal = totalSubTotal.add(farmerSubTotal);
            totalShippingFee = totalShippingFee.add(farmerShippingFee);
        }

        // Tính discount tổng dựa trên tổng subTotal và buyer
        totalDiscount = calculateDiscount(buyer, totalSubTotal /*, request.getVoucherCode() */);
        BigDecimal totalAmount = totalSubTotal.add(totalShippingFee).subtract(totalDiscount);

        return new OrderCalculationResponse(totalSubTotal, totalShippingFee, totalDiscount, totalAmount, finalOrderType);
    }

    @Override
    @Transactional
    public OrderResponse confirmBankTransferPayment(Long orderId, String bankTransactionCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new BadRequestException("Order was not placed with Bank Transfer method.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Order has already been paid.");
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.AWAITING_PAYMENT) { // Giả sử có AWAITING_PAYMENT
            order.setStatus(OrderStatus.CONFIRMED); // Hoặc PROCESSING
        }

        // Tạo bản ghi Payment
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentGateway(PaymentMethod.BANK_TRANSFER.name());
        payment.setStatus(PaymentTransactionStatus.SUCCESS);
        payment.setPaymentTime(LocalDateTime.now());
        payment.setTransactionCode(bankTransactionCode); // Lưu mã giao dịch ngân hàng nếu có
        payment.setGatewayMessage("Payment confirmed manually by admin.");
        paymentRepository.save(payment);

        Order savedOrder = orderRepository.save(order);
        log.info("Bank transfer payment confirmed for order {}", order.getOrderCode());
        notificationService.sendPaymentSuccessNotification(savedOrder); // Thông báo cho buyer
        return orderMapper.toOrderResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse confirmOrderPaymentByAdmin(Long orderId, PaymentMethod paymentMethodConfirmed, String transactionReference, String adminNotes) {
        Order order = orderRepository.findById(orderId) // Admin có thể xem mọi đơn hàng
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
// Kiểm tra xem phương thức thanh toán của đơn hàng có khớp với phương thức admin xác nhận không (tùy chọn)
// if (order.getPaymentMethod() != paymentMethodConfirmed) {
// log.warn("Admin attempted to confirm payment for order {} with method {}, but order was placed with {}",
// orderId, paymentMethodConfirmed, order.getPaymentMethod());
// // Có thể throw lỗi hoặc cho phép ghi đè paymentMethod nếu cần
// }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Đơn hàng này đã được ghi nhận thanh toán.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Không thể xác nhận thanh toán cho đơn hàng đã hủy hoặc đã hoàn thành.");
        }
        order.setPaymentStatus(PaymentStatus.PAID);
// Khi Admin xác nhận thanh toán, chuyển đơn hàng sang trạng thái phù hợp
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            order.setStatus(OrderStatus.CONFIRMED); // Hoặc PROCESSING tùy quy trình
        }
// Tạo bản ghi Payment
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentGateway(paymentMethodConfirmed.name()); // Phương thức mà Admin xác nhận
        payment.setStatus(PaymentTransactionStatus.SUCCESS);
        payment.setPaymentTime(LocalDateTime.now());
        payment.setTransactionCode(transactionReference); // Mã giao dịch do Admin nhập (nếu có)
        payment.setGatewayMessage("Payment confirmed by Admin." + (StringUtils.hasText(adminNotes) ? " Notes: " + adminNotes : ""));
        paymentRepository.save(payment);
        Order savedOrder = orderRepository.save(order);
        log.info("Admin confirmed {} payment for order {}", paymentMethodConfirmed.name(), order.getOrderCode());
// Gửi thông báo cho người mua rằng thanh toán đã được xác nhận
        notificationService.sendPaymentSuccessNotification(savedOrder);
// Gửi thông báo cho người mua rằng trạng thái đơn hàng đã thay đổi
        notificationService.sendOrderStatusUpdateNotification(savedOrder, OrderStatus.PENDING); // Giả sử trạng thái trước đó là PENDING
// (Tùy chọn) Gửi thông báo cho Farmer rằng đơn hàng đã được thanh toán và có thể chuẩn bị hàng
// notificationService.sendOrderPaidNotificationToFarmer(savedOrder);
        return orderMapper.toOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public BankTransferInfoResponse getBankTransferInfoForOrder(Long orderId, Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        Order order = orderRepository.findByIdWithDetails(orderId) // Dùng query có fetch
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !order.getBuyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin thanh toán cho đơn hàng này.");
        }

        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new BadRequestException("Đơn hàng này không sử dụng phương thức chuyển khoản ngân hàng.");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING && order.getPaymentStatus() != PaymentStatus.AWAITING_PAYMENT_TERM) {
            throw new BadRequestException("Đơn hàng này không ở trạng thái chờ thanh toán chuyển khoản.");
        }

        String transferContent = "CK " + order.getOrderCode();
        String qrCodeDataString;

        // Tạo chuỗi cho QR code
        // Lựa chọn 1: Tạo URL đến dịch vụ tạo ảnh QR (như VietQR.io)
        if (StringUtils.hasText(qrServiceUrlBase) && StringUtils.hasText(appBankBin) && StringUtils.hasText(appBankAccountNumber)) {
            try {
                String encodedOrderInfo = URLEncoder.encode(transferContent, StandardCharsets.UTF_8.toString());
                String encodedAccountName = URLEncoder.encode(appBankAccountName, StandardCharsets.UTF_8.toString());
                qrCodeDataString = String.format("%s/%s-%s-%s.png?amount=%s&addInfo=%s&accountName=%s",
                        qrServiceUrlBase.replaceAll("/$", ""), // Xóa dấu / ở cuối nếu có
                        appBankBin,
                        appBankAccountNumber,
                        qrTemplate,
                        order.getTotalAmount().toPlainString(),
                        encodedOrderInfo,
                        encodedAccountName
                );
                log.info("Generated QR Image URL for order {}: {}", order.getOrderCode(), qrCodeDataString);
            } catch (Exception e) {
                log.error("Error generating QR Image URL for order {}: {}", order.getOrderCode(), e.getMessage());
                qrCodeDataString = "Lỗi tạo mã QR (URL)";
            }
        } else {
            // Lựa chọn 2: Tạo chuỗi dữ liệu thô theo chuẩn VietQR (để frontend tự render)
            // Đây là một ví dụ đơn giản hóa, bạn cần tham khảo chuẩn VietQR/Napas247 để có chuỗi chính xác
            // Payload version 000201, Point of Initiation 010212 (static QR with beneficiary)
            // Merchant Account Info (tag 38)
            //   GUID (tag 00) - A000000727 (NAPAS247 by VNPAY)
            //   Beneficiary Organization (tag 01) - Bank BIN (tag 00) + Account Number (tag 01)
            //     Bank BIN (tag 00) - appBankBin
            //     Account Number (tag 01) - appBankAccountNumber
            // Transaction Currency (tag 53) - 03704 (VND)
            // Transaction Amount (tag 54) - order.getTotalAmount()
            // Country Code (tag 58) - 02VN
            // Additional Data (tag 62)
            //   Purpose of Transaction (tag 08) - transferContent
            // CRC (tag 63) - 04XXXX

            // Ví dụ tạo chuỗi data đơn giản hơn mà nhiều thư viện QR có thể hiểu
            // (Không hoàn toàn theo chuẩn VietQR nhưng chứa đủ thông tin cơ bản)
            // Hoặc bạn có thể tìm thư viện Java để tạo chuỗi VietQR đầy đủ.
            // String data = "STK: " + appBankAccountNumber + "\n" +
            //               "Ngân hàng: " + appBankNameDisplay + "\n" +
            //               "Số tiền: " + order.getTotalAmount().toPlainString() + "\n" +
            //               "Nội dung: " + transferContent;
            // qrCodeDataString = data;

            // Để nhất quán với ví dụ trước, nếu không có qrServiceUrlBase, ta sẽ báo lỗi
            // hoặc trả về null/chuỗi rỗng cho qrCodeDataString
            log.warn("QR Service URL Base not configured. Cannot generate QR image URL for order {}", order.getOrderCode());
            qrCodeDataString = null; // Hoặc một thông báo lỗi
        }


        return new BankTransferInfoResponse(
                appBankAccountName,
                appBankAccountNumber,
                appBankNameDisplay,
                order.getTotalAmount(),
                order.getOrderCode(),
                transferContent,
                qrCodeDataString
        );
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


}