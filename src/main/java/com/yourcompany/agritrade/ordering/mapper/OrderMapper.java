package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.catalog.mapper.FarmerInfoMapper; // Import FarmerInfoMapper
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.usermanagement.domain.User; // Import User
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper; // Import UserMapper
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.data.domain.Page; // Import Page

import java.util.List;

@Mapper(componentModel = "spring", uses = {
        OrderItemMapper.class,
        PaymentMapper.class,
        UserMapper.class,         // Để map buyer
        FarmerInfoMapper.class    // Để map farmer
})
public abstract class OrderMapper { // Đổi thành abstract class

    @Autowired // Inject các mapper cần thiết
    protected UserMapper userMapper;
    @Autowired
    protected FarmerInfoMapper farmerInfoMapper;
    @Autowired
    protected OrderItemMapper orderItemMapper;
    @Autowired
    protected PaymentMapper paymentMapper;


    // Map sang OrderResponse (chi tiết)
    // MapStruct sẽ dùng các mapper trong 'uses' cho các trường tương ứng
    @Mapping(target = "buyer", source = "buyer", qualifiedByName = "toUserResponse") // *** Chỉ định tên ***
    @Mapping(target = "farmer", source = "farmer", qualifiedByName = "mapUserToFarmerInfo") // Dùng FarmerInfoMapper thông qua helper
    @Mapping(target = "orderItems", source = "orderItems") // Dùng OrderItemMapper
    @Mapping(target = "payments", source = "payments") // Dùng PaymentMapper
    public abstract OrderResponse toOrderResponse(Order order);

    // Map sang OrderSummaryResponse (tóm tắt)
    @Mapping(target = "buyerName", source = "buyer.fullName") // Lấy tên trực tiếp
    @Mapping(target = "farmerName", source = "farmer", qualifiedByName = "mapFarmerToFarmName") // Helper lấy tên farm
    public abstract OrderSummaryResponse toOrderSummaryResponse(Order order);

    // Map Page<Order> sang Page<OrderSummaryResponse>
    public Page<OrderSummaryResponse> toOrderSummaryResponsePage(Page<Order> orderPage) {
        return orderPage.map(this::toOrderSummaryResponse);
    }

    // Map List<Order> sang List<OrderSummaryResponse>
    public abstract List<OrderSummaryResponse> toOrderSummaryResponseList(List<Order> orders);


    // Helper method để gọi FarmerInfoMapper (vì nguồn là User)
    @Named("mapUserToFarmerInfo")
    protected FarmerInfoResponse mapUserToFarmerInfo(User farmer) {
        if (farmer == null) return null;
        // Cần lấy FarmerProfile nếu FarmerInfoMapper yêu cầu
        // Hoặc dùng mapper đơn giản chỉ lấy thông tin từ User
        return farmerInfoMapper.userToFarmerInfoResponse(farmer);
    }

    // Helper method để lấy tên farm (ví dụ)
    @Named("mapFarmerToFarmName")
    protected String mapFarmerToFarmName(User farmer) {
        // Cần logic để lấy FarmerProfile và tên farm từ farmer.getId()
        // Ví dụ: return farmerProfileRepository.findById(farmer.getId()).map(FarmerProfile::getFarmName).orElse(farmer.getFullName());
        return farmer != null ? farmer.getFullName() : null; // Tạm thời lấy tên user
    }
}