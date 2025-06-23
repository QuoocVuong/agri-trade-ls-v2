package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.catalog.mapper.FarmerInfoMapper;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceInfoResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

@Mapper(
    componentModel = "spring",
    uses = {
      OrderItemMapper.class,
      PaymentMapper.class,
      UserMapper.class, // Để map buyer
      FarmerInfoMapper.class // Để map farmer
    })
public abstract class OrderMapper {

  @Autowired // Inject các mapper cần thiết
  protected UserMapper userMapper;
  @Autowired protected FarmerInfoMapper farmerInfoMapper;
  @Autowired protected OrderItemMapper orderItemMapper;
  @Autowired protected PaymentMapper paymentMapper;
  @Autowired protected InvoiceRepository invoiceRepository;

  // Map sang OrderResponse (chi tiết)
  // MapStruct sẽ dùng các mapper trong 'uses' cho các trường tương ứng
  @Mapping(target = "buyer", source = "buyer") // *** Chỉ định tên ***
  @Mapping(
      target = "farmer",
      source = "farmer",
      qualifiedByName = "mapUserAndProfileToFarmerInfo") // Dùng FarmerInfoMapper thông qua helper
  @Mapping(target = "orderItems", source = "orderItems") // Dùng OrderItemMapper
  @Mapping(target = "payments", source = "payments") // Dùng PaymentMapper
  @Mapping(target = "invoiceInfo", source = "order", qualifiedByName = "mapOrderToInvoiceInfo")
  @Mapping(
      target = "sourceRequestId",
      source = "sourceRequest.id") // Lấy ID từ đối tượng sourceRequest lồng nhau
  public abstract OrderResponse toOrderResponse(Order order);

  @Named("mapOrderToInvoiceInfo")
  protected InvoiceInfoResponse mapOrderToInvoiceInfo(Order order) {
    if (order == null || order.getPaymentMethod() != PaymentMethod.INVOICE) {
      return null; // Chỉ tạo InvoiceInfo cho đơn hàng công nợ
    }

    // Tìm Invoice liên quan đến Order này
    //  Invoice được tạo khi đơn hàng INVOICE được tạo
    // thì ở đây invoiceRepository.findByOrderId() sẽ luôn tìm thấy.
    return invoiceRepository
        .findByOrderId(order.getId())
        .map(
            invoice ->
                new InvoiceInfoResponse(
                    invoice.getInvoiceNumber(),
                    invoice.getIssueDate(),
                    invoice.getDueDate(),
                    invoice.getStatus(),
                    invoice.getId()))
        .orElse(null); // Trả về null nếu không tìm thấy Invoice
  }

  // Map sang OrderSummaryResponse (tóm tắt)
  @Mapping(target = "buyerName", source = "buyer.fullName") // Lấy tên trực tiếp
  @Mapping(
      target = "farmerName",
      source = "farmer",
      qualifiedByName = "mapUserToBestFarmerName") // Helper lấy tên farm
  public abstract OrderSummaryResponse toOrderSummaryResponse(Order order);

  // Map Page<Order> sang Page<OrderSummaryResponse>
  public Page<OrderSummaryResponse> toOrderSummaryResponsePage(Page<Order> orderPage) {
    return orderPage.map(this::toOrderSummaryResponse);
  }

  // Map List<Order> sang List<OrderSummaryResponse>
  public abstract List<OrderSummaryResponse> toOrderSummaryResponseList(List<Order> orders);

  // Helper method để gọi FarmerInfoMapper (vì nguồn là User)
  @Named("mapUserAndProfileToFarmerInfo") // Đổi tên cho rõ ràng
  protected FarmerInfoResponse mapUserAndProfileToFarmerInfo(User farmer) {
    if (farmer == null) return null;
    // Dòng này yêu cầu FarmerProfile phải được load sẵn (do JOIN FETCH trong
    // Service/Repo)
    FarmerProfile profile = farmer.getFarmerProfile();
    // Gọi phương thức của FarmerInfoMapper nhận cả User và Profile
    FarmerInfoResponse info = farmerInfoMapper.toFarmerInfoResponse(farmer, profile);

    // Fallback logic nếu cần (ví dụ: nếu profile null hoặc farmName trong profile là null)
    if (info != null
        && (info.getFarmName() == null || info.getFarmName().isEmpty())
        && farmer.getFullName() != null) {
      info.setFarmName(
          farmer.getFullName()); // Dùng fullName của User làm dự phòng nếu farmName trống
    }
    // Đảm bảo fullName luôn được map vào FarmerInfoResponse
    if (info != null && info.getFullName() == null && farmer.getFullName() != null) {
      info.setFullName(farmer.getFullName());
    }

    return info;
  }

  // Helper method để lấy tên farm
  @Named("mapUserToBestFarmerName")
  protected String mapUserToBestFarmerName(User farmer) {
    if (farmer == null) return null;
    //  Dòng này yêu cầu FarmerProfile phải được load sẵn
    FarmerProfile profile = farmer.getFarmerProfile();
    if (profile != null && profile.getFarmName() != null && !profile.getFarmName().isEmpty()) {
      return profile.getFarmName(); // Ưu tiên farmName
    }
    return farmer.getFullName(); // Fallback sang fullName
  }

  // Helper method để map User sang UserResponse (nếu UserMapper không có sẵn)
  @Named("toUserResponse")
  protected UserResponse toUserResponse(User user) {
    if (user == null) return null;
    return userMapper.toUserResponse(user); // Gọi UserMapper đã inject
  }
}
