package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.*;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequest;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import com.yourcompany.agritrade.ordering.dto.request.SupplyOrderPlacementRequest;
import com.yourcompany.agritrade.ordering.dto.response.SupplyOrderRequestResponse;
import com.yourcompany.agritrade.ordering.mapper.SupplyOrderRequestMapper;
import com.yourcompany.agritrade.ordering.repository.SupplyOrderRequestRepository;
import com.yourcompany.agritrade.ordering.repository.specification.SupplyOrderRequestSpecifications;
import com.yourcompany.agritrade.ordering.service.OrderService;
import com.yourcompany.agritrade.ordering.service.SupplyOrderRequestService;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final BusinessProfileRepository businessProfileRepository;
  private final FarmerProfileRepository farmerProfileRepository;

  @Override
  @Transactional
  public SupplyOrderRequestResponse createSupplyOrderRequest(
      Authentication authentication, SupplyOrderPlacementRequest requestDto) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();

    // Gọi lại hàm kiểm tra quyền ở đầu để tái sử dụng logic
    checkCreatePermission(authentication);

    User farmer =
        userRepository
            .findById(requestDto.getFarmerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Farmer", "id", requestDto.getFarmerId()));
    Product product =
        productRepository
            .findById(requestDto.getProductId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Product", "id", requestDto.getProductId()));

    if (buyer.getId().equals(farmer.getId())) {
      throw new BadRequestException("Cannot send supply request to yourself.");
    }
    // Kiểm tra sản phẩm có thuộc farmer không
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
    log.info(
        "SupplyOrderRequest {} created by buyer {} for farmer {} and product {}",
        savedRequest.getId(),
        buyer.getId(),
        farmer.getId(),
        product.getId());

    // Gửi thông báo cho Farmer
    // notificationService.sendNewSupplyOrderRequestNotification(savedRequest); // Cần tạo hàm này

    return requestMapper.toSupplyOrderRequestResponse(savedRequest);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SupplyOrderRequestResponse> getMySentRequests(
      Authentication authentication, SupplyOrderRequestStatus status, Pageable pageable) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();

    Specification<SupplyOrderRequest> spec =
        Specification.where(SupplyOrderRequestSpecifications.forBuyer(buyer.getId()))
            .and(SupplyOrderRequestSpecifications.hasStatus(status));

    return requestRepository
        .findAll(spec, pageable)
        .map(requestMapper::toSupplyOrderRequestResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SupplyOrderRequestResponse> getMyReceivedRequests(
      Authentication authentication, SupplyOrderRequestStatus status, Pageable pageable) {
    User farmer = SecurityUtils.getCurrentAuthenticatedUser();
    if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
      throw new AccessDeniedException("User is not a farmer.");
    }
    Specification<SupplyOrderRequest> spec =
        Specification.where(SupplyOrderRequestSpecifications.forFarmer(farmer.getId()))
            .and(SupplyOrderRequestSpecifications.hasStatus(status));

    return requestRepository
        .findAll(spec, pageable)
        .map(requestMapper::toSupplyOrderRequestResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public SupplyOrderRequestResponse getRequestDetails(
      Authentication authentication, Long requestId) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    SupplyOrderRequest request =
        requestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));
    // Kiểm tra quyền xem (là buyer hoặc farmer của request)
    if (!request.getBuyer().getId().equals(currentUser.getId())
        && !request.getFarmer().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("You do not have permission to view this request.");
    }
    return requestMapper.toSupplyOrderRequestResponse(request);
  }

  @Override
  @Transactional
  public SupplyOrderRequestResponse acceptSupplyOrderRequest(
      Authentication authentication, Long requestId) {
    User farmer = SecurityUtils.getCurrentAuthenticatedUser();
    if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
      throw new AccessDeniedException("Only farmers can accept supply requests.");
    }
    SupplyOrderRequest supplyRequest =
        requestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

    if (!supplyRequest.getFarmer().getId().equals(farmer.getId())) {
      throw new AccessDeniedException("This request was not sent to you.");
    }
    if (supplyRequest.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION
        && supplyRequest.getStatus()
            != SupplyOrderRequestStatus.NEGOTIATING) { // Cho phép accept từ NEGOTIATING
      throw new BadRequestException(
          "Request cannot be accepted from its current status: " + supplyRequest.getStatus());
    }

    // Chỉ cập nhật trạng thái. Việc tạo Order sẽ do một API khác xử lý.
    supplyRequest.setStatus(SupplyOrderRequestStatus.FARMER_ACCEPTED); // Trạng thái mới
    SupplyOrderRequest savedRequest = requestRepository.save(supplyRequest);

    log.info(
        "Farmer {} accepted SupplyOrderRequest {}. Status changed to FARMER_ACCEPTED.",
        farmer.getId(),
        requestId);

    // (Tùy chọn) Gửi thông báo cho Buyer rằng yêu cầu đã được chấp nhận và đang chờ Farmer tạo đơn
    // hàng.
    // notificationService.sendSupplyRequestAcceptedNotification(savedRequest);

    return requestMapper.toSupplyOrderRequestResponse(savedRequest);
  }

  @Override
  @Transactional
  public SupplyOrderRequestResponse rejectSupplyOrderRequest(
      Authentication authentication, Long requestId, String reason) {
    User farmer = SecurityUtils.getCurrentAuthenticatedUser();
    if (farmer.getRoles().stream().noneMatch(r -> r.getName() == RoleType.ROLE_FARMER)) {
      throw new AccessDeniedException("Only farmers can reject supply requests.");
    }
    SupplyOrderRequest supplyRequest =
        requestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

    if (!supplyRequest.getFarmer().getId().equals(farmer.getId())) {
      throw new AccessDeniedException("This request was not sent to you.");
    }
    if (supplyRequest.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION
        && supplyRequest.getStatus() != SupplyOrderRequestStatus.NEGOTIATING) {
      throw new BadRequestException("Request cannot be rejected from its current status.");
    }

    supplyRequest.setStatus(SupplyOrderRequestStatus.FARMER_REJECTED);
    supplyRequest.setFarmerResponseMessage(reason);
    SupplyOrderRequest savedRequest = requestRepository.save(supplyRequest);

    log.info(
        "Farmer {} rejected SupplyOrderRequest {}. Reason: {}", farmer.getId(), requestId, reason);
    // Gửi thông báo cho Buyer
    // notificationService.sendSupplyOrderRequestRejectedNotification(savedRequest);

    return requestMapper.toSupplyOrderRequestResponse(savedRequest);
  }

  @Override
  @Transactional
  public void cancelSupplyOrderRequestByBuyer(Authentication authentication, Long requestId) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser(); // Hoặc hàm helper của bạn

    SupplyOrderRequest request =
        requestRepository
            .findById(requestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("SupplyOrderRequest", "id", requestId));

    // Kiểm tra quyền: Buyer phải là người tạo request
    if (!request.getBuyer().getId().equals(buyer.getId())) {
      throw new AccessDeniedException("You do not have permission to cancel this request.");
    }

    // Kiểm tra trạng thái: Chỉ cho phép hủy khi Farmer chưa xử lý
    if (request.getStatus() != SupplyOrderRequestStatus.PENDING_FARMER_ACTION) {
      throw new BadRequestException(
          "This request cannot be cancelled as it's already being processed or has been finalized. Current status: "
              + request.getStatus());
    }

    request.setStatus(SupplyOrderRequestStatus.BUYER_CANCELLED);
    requestRepository.save(request);
    log.info("Buyer {} cancelled SupplyOrderRequest {}", buyer.getId(), requestId);

    // notificationService.sendSupplyRequestCancelledByBuyerNotification(request.getFarmer(),
    // request);
  }

  @Override
  public void checkCreatePermission(Authentication authentication) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();

    // 1. Lấy danh sách các vai trò của người dùng
    Set<Role> userRoles = buyer.getRoles();

    // 2. Kiểm tra xem người dùng có vai trò FARMER hoặc BUSINESS_BUYER không
    boolean isFarmer = userRoles.stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER);

    boolean isBusinessBuyer =
        userRoles.stream().anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER);

    // 3. Nếu người dùng KHÔNG phải là Farmer VÀ cũng KHÔNG phải là Business Buyer
    if (!isFarmer && !isBusinessBuyer) {
      // Ném ra exception yêu cầu nâng cấp tài khoản.
      // Thông báo này vẫn hợp lý vì người dùng có thể chọn nâng cấp lên Business Buyer.
      throw new BusinessAccountRequiredException(
          "Chức năng này chỉ dành cho tài khoản Nông dân hoặc Doanh nghiệp. Vui lòng đăng ký hồ sơ phù hợp để tiếp tục.");
    }

    // 4. Nếu người dùng là BUSINESS_BUYER, yêu cầu phải có hồ sơ doanh nghiệp
    if (isBusinessBuyer) {
      businessProfileRepository
          .findById(buyer.getId())
          .orElseThrow(
              () ->
                  new BusinessProfileRequiredException(
                      "Bạn cần hoàn thiện hồ sơ doanh nghiệp trước khi gửi yêu cầu cung ứng."));
    }

    // 5. Nếu người dùng là FARMER (nhưng không phải Business Buyer), yêu cầu phải có hồ sơ nông dân
    // Điều này đảm bảo chỉ những nông dân đã đăng ký hồ sơ mới có thể mua hàng B2B.
    if (isFarmer && !isBusinessBuyer) {
      farmerProfileRepository
          .findById(buyer.getId())
          .orElseThrow(
              () ->
                  new FarmerProfileRequiredException( // <<< TẠO EXCEPTION MỚI
                      "Bạn cần hoàn thiện hồ sơ nông dân trước khi gửi yêu cầu cung ứng."));
    }

    // Nếu người dùng có cả 2 vai trò, chỉ cần 1 trong 2 hồ sơ tồn tại là được
  }
}
