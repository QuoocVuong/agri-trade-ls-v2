// src/main/java/com/yourcompany/agritrade/ordering/service/impl/SupplyOrderRequestServiceImpl.java
package com.yourcompany.agritrade.ordering.service.impl;

// ... (imports cho các class đã tạo ở trên, OrderService, NotificationService, etc.) ...
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequest;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import com.yourcompany.agritrade.ordering.dto.request.AgreedOrderRequest; // Import AgreedOrderRequest
import com.yourcompany.agritrade.ordering.dto.request.AgreedOrderItemRequest; // Import AgreedOrderItemRequest
import com.yourcompany.agritrade.ordering.dto.request.SupplyOrderPlacementRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.SupplyOrderRequestResponse;
import com.yourcompany.agritrade.ordering.mapper.SupplyOrderRequestMapper;
import com.yourcompany.agritrade.ordering.repository.SupplyOrderRequestRepository;
import com.yourcompany.agritrade.ordering.service.OrderService; // Inject OrderService
import com.yourcompany.agritrade.notification.service.NotificationService; // Inject NotificationService
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.ordering.service.SupplyOrderRequestService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class SupplyOrderRequestServiceImpl implements SupplyOrderRequestService {

    private final SupplyOrderRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SupplyOrderRequestMapper requestMapper;
    private final OrderService orderService; // Để tạo AgreedOrder khi Farmer chấp nhận
    private final NotificationService notificationService; // Để gửi thông báo

    @Override
    @Transactional
    public SupplyOrderRequestResponse createSupplyOrderRequest(Authentication authentication, SupplyOrderPlacementRequest requestDto) {
        User buyer = SecurityUtils.getCurrentAuthenticatedUser(); // Hoặc getUserFromAuthentication
        User farmer = userRepository.findById(requestDto.getFarmerId())
                .orElseThrow(() -> new ResourceNotFoundException("Farmer", "id", requestDto.getFarmerId()));
        Product product = productRepository.findById(requestDto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", requestDto.getProductId()));

        if (buyer.getId().equals(farmer.getId())) {
            throw new BadRequestException("Cannot send supply request to yourself.");
        }
        // Kiểm tra sản phẩm có thuộc farmer không (tùy chọn, nhưng nên có)
        if (!product.getFarmer().getId().equals(farmer.getId())) {
            throw new BadRequestException("Product does not belong to the specified farmer.");
        }

        SupplyOrderRequest newRequest = new SupplyOrderRequest();
        newRequest.setBuyer(buyer);
        newRequest.setFarmer(farmer);
        newRequest.setProduct(product);
        newRequest.setRequestedQuantity(requestDto.getRequestedQuantity());
        newRequest.setRequestedUnit(requestDto.getRequestedUnit());
        newRequest.setProposedPricePerUnit(requestDto.getProposedPricePerUnit());
        newRequest.setBuyerNotes(requestDto.getBuyerNotes());
        // Copy thông tin giao hàng
        newRequest.setShippingFullName(requestDto.getShippingFullName());
        newRequest.setShippingPhoneNumber(requestDto.getShippingPhoneNumber());
        newRequest.setShippingAddressDetail(requestDto.getShippingAddressDetail());
        newRequest.setShippingProvinceCode(requestDto.getShippingProvinceCode());
        newRequest.setShippingDistrictCode(requestDto.getShippingDistrictCode());
        newRequest.setShippingWardCode(requestDto.getShippingWardCode());
        newRequest.setExpectedDeliveryDate(requestDto.getExpectedDeliveryDate());
        // status mặc định là PENDING_FARMER_ACTION

        SupplyOrderRequest savedRequest = requestRepository.save(newRequest);
        log.info("SupplyOrderRequest {} created by buyer {} for farmer {} and product {}",
                savedRequest.getId(), buyer.getId(), farmer.getId(), product.getId());

        // Gửi thông báo cho Farmer
        // notificationService.sendNewSupplyOrderRequestNotification(savedRequest); // Cần tạo hàm này

        return requestMapper.toSupplyOrderRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplyOrderRequestResponse> getMySentRequests(Authentication authentication, Pageable pageable) {
        User buyer = SecurityUtils.getCurrentAuthenticatedUser();
        return requestRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId(), pageable)
                .map(requestMapper::toSupplyOrderRequestResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplyOrderRequestResponse> getMyReceivedRequests(Authentication authentication, Pageable pageable) {
        User farmer = SecurityUtils.getCurrentAuthenticatedUser();
        if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
            throw new AccessDeniedException("User is not a farmer.");
        }
        return requestRepository.findByFarmerIdOrderByCreatedAtDesc(farmer.getId(), pageable)
                .map(requestMapper::toSupplyOrderRequestResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplyOrderRequestResponse getRequestDetails(Authentication authentication, Long requestId) {
        User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
        SupplyOrderRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));
        // Kiểm tra quyền xem (là buyer hoặc farmer của request)
        if (!request.getBuyer().getId().equals(currentUser.getId()) && !request.getFarmer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to view this request.");
        }
        return requestMapper.toSupplyOrderRequestResponse(request);
    }

    @Override
    @Transactional
    public OrderResponse acceptSupplyOrderRequest(Authentication authentication, Long requestId) {
        User farmer = SecurityUtils.getCurrentAuthenticatedUser();
        if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
            throw new AccessDeniedException("Only farmers can accept supply requests.");
        }
        SupplyOrderRequest supplyRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

        if (!supplyRequest.getFarmer().getId().equals(farmer.getId())) {
            throw new AccessDeniedException("This request was not sent to you.");
        }
        if (supplyRequest.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION &&
                supplyRequest.getStatus() != SupplyOrderRequestStatus.NEGOTIATING) { // Cho phép accept từ NEGOTIATING
            throw new BadRequestException("Request cannot be accepted from its current status: " + supplyRequest.getStatus());
        }

        // Tạo AgreedOrderRequest từ SupplyOrderRequest
        AgreedOrderRequest agreedOrderDto = new AgreedOrderRequest();
        agreedOrderDto.setBuyerId(supplyRequest.getBuyer().getId());
        // farmerId sẽ được lấy từ Authentication trong orderService.createAgreedOrder

        AgreedOrderItemRequest agreedItem = new AgreedOrderItemRequest();
        agreedItem.setProductId(supplyRequest.getProduct().getId());
        agreedItem.setProductName(supplyRequest.getProduct().getName()); // Lấy tên gốc, Farmer có thể sửa khi tạo đơn
        agreedItem.setUnit(supplyRequest.getRequestedUnit());
        agreedItem.setQuantity(supplyRequest.getRequestedQuantity());
        // Giá: Ưu tiên giá đề xuất của buyer, nếu không có thì Farmer tự nhập khi tạo đơn
        // Hoặc Farmer có thể sửa giá này trong form tạo AgreedOrder
        agreedItem.setPricePerUnit(
                supplyRequest.getProposedPricePerUnit() != null ?
                        supplyRequest.getProposedPricePerUnit() :
                        (supplyRequest.getProduct().getReferenceWholesalePrice() != null ?
                                supplyRequest.getProduct().getReferenceWholesalePrice() :
                                BigDecimal.ZERO)
        );



        agreedOrderDto.setItems(List.of(agreedItem));

        // Tổng tiền: Farmer sẽ nhập/xác nhận lại khi tạo AgreedOrder.
        // Ở đây có thể tính tạm dựa trên proposedPrice hoặc referenceWholesalePrice
        BigDecimal tempTotal = BigDecimal.ZERO;
        if (supplyRequest.getProposedPricePerUnit() != null) {
            tempTotal = supplyRequest.getProposedPricePerUnit().multiply(BigDecimal.valueOf(supplyRequest.getRequestedQuantity()));
        } else if (supplyRequest.getProduct().getReferenceWholesalePrice() != null) {
            tempTotal = supplyRequest.getProduct().getReferenceWholesalePrice().multiply(BigDecimal.valueOf(supplyRequest.getRequestedQuantity()));
        }
        agreedOrderDto.setAgreedTotalAmount(tempTotal); // Farmer sẽ xác nhận lại

        // Phương thức thanh toán: Farmer sẽ chọn khi tạo AgreedOrder. Mặc định là BANK_TRANSFER.
        agreedOrderDto.setAgreedPaymentMethod(PaymentMethod.BANK_TRANSFER);

        // Copy thông tin giao hàng từ request
        agreedOrderDto.setShippingFullName(supplyRequest.getShippingFullName());
        agreedOrderDto.setShippingPhoneNumber(supplyRequest.getShippingPhoneNumber());
        agreedOrderDto.setShippingAddressDetail(supplyRequest.getShippingAddressDetail());
        agreedOrderDto.setShippingProvinceCode(supplyRequest.getShippingProvinceCode());
        agreedOrderDto.setShippingDistrictCode(supplyRequest.getShippingDistrictCode());
        agreedOrderDto.setShippingWardCode(supplyRequest.getShippingWardCode());
        agreedOrderDto.setExpectedDeliveryDate(supplyRequest.getExpectedDeliveryDate());
        agreedOrderDto.setNotes("Đơn hàng được tạo từ yêu cầu #" + supplyRequest.getId() + ". " + (supplyRequest.getBuyerNotes() != null ? supplyRequest.getBuyerNotes() : ""));

        // Gọi OrderService để tạo "Đơn hàng thỏa thuận"
        // Authentication ở đây là của Farmer
        OrderResponse createdOrder = orderService.createAgreedOrder(authentication, agreedOrderDto);

        supplyRequest.setStatus(SupplyOrderRequestStatus.FARMER_ACCEPTED);
        requestRepository.save(supplyRequest);

        log.info("Farmer {} accepted SupplyOrderRequest {} and created Order {}",
                farmer.getId(), requestId, createdOrder.getOrderCode());

        // Gửi thông báo cho Buyer
        // notificationService.sendSupplyOrderRequestAcceptedNotification(supplyRequest, createdOrder);

        return createdOrder;
    }

    @Override
    @Transactional
    public SupplyOrderRequestResponse rejectSupplyOrderRequest(Authentication authentication, Long requestId, String reason) {
        User farmer = SecurityUtils.getCurrentAuthenticatedUser();
        if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
            throw new AccessDeniedException("Only farmers can reject supply requests.");
        }
        SupplyOrderRequest supplyRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

        if (!supplyRequest.getFarmer().getId().equals(farmer.getId())) {
            throw new AccessDeniedException("This request was not sent to you.");
        }
        if (supplyRequest.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION &&
                supplyRequest.getStatus() != SupplyOrderRequestStatus.NEGOTIATING) {
            throw new BadRequestException("Request cannot be rejected from its current status.");
        }

        supplyRequest.setStatus(SupplyOrderRequestStatus.FARMER_REJECTED);
        supplyRequest.setFarmerResponseMessage(reason);
        SupplyOrderRequest savedRequest = requestRepository.save(supplyRequest);

        log.info("Farmer {} rejected SupplyOrderRequest {}. Reason: {}", farmer.getId(), requestId, reason);
        // Gửi thông báo cho Buyer
        // notificationService.sendSupplyOrderRequestRejectedNotification(savedRequest);

        return requestMapper.toSupplyOrderRequestResponse(savedRequest);
    }

    @Override
    @Transactional
    public void cancelSupplyOrderRequestByBuyer(Authentication authentication, Long requestId) {
        User buyer = SecurityUtils.getCurrentAuthenticatedUser(); // Hoặc hàm helper của bạn

        SupplyOrderRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

        // Kiểm tra quyền: Buyer phải là người tạo request
        if (!request.getBuyer().getId().equals(buyer.getId())) {
            throw new AccessDeniedException("You do not have permission to cancel this request.");
        }

        // Kiểm tra trạng thái: Chỉ cho phép hủy khi Farmer chưa xử lý
        if (request.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION) {
            throw new BadRequestException("This request cannot be cancelled as it's already being processed or has been finalized. Current status: " + request.getStatus());
        }

        request.setStatus(SupplyOrderRequestStatus.BUYER_CANCELLED);
        requestRepository.save(request);
        log.info("Buyer {} cancelled SupplyOrderRequest {}", buyer.getId(), requestId);

        // TODO: Gửi thông báo cho Farmer rằng Buyer đã hủy yêu cầu
        // notificationService.sendSupplyRequestCancelledByBuyerNotification(request.getFarmer(), request);
    }
}