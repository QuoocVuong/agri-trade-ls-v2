package com.yourcompany.agritrade.interaction.mapper;

import com.yourcompany.agritrade.catalog.mapper.ProductMapper;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper; // Import UserMapper
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page; // Import Page

import java.util.List;

// uses UserMapper để map thông tin consumer
@Mapper(componentModel = "spring", uses = {UserMapper.class, ProductMapper.class})
public interface ReviewMapper {

    // Map Review entity sang ReviewResponse DTO
    //@Mapping(target = "productId", source = "product.id") // Lấy ID từ product entity lồng nhau
    @Mapping(target = "orderId", source = "order.id")   // Lấy ID từ order entity lồng nhau
    @Mapping(target = "consumer", source = "consumer") // Dùng UserMapper map sang UserInfoSimpleResponse
    @Mapping(target = "productInfo", source = "product") // <<< Map product entity sang productInfo DTO
    ReviewResponse toReviewResponse(Review review);

    List<ReviewResponse> toReviewResponseList(List<Review> reviews);

    // Map Page<Review> sang Page<ReviewResponse>
    default Page<ReviewResponse> toReviewResponsePage(Page<Review> reviewPage) {
        return reviewPage.map(this::toReviewResponse);
    }

    // Map từ ReviewRequest DTO sang Review entity (khi tạo mới)
    // Bỏ qua các trường được tạo tự động hoặc lấy từ context
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true) // Sẽ set trong service
    @Mapping(target = "consumer", ignore = true) // Sẽ set trong service
    @Mapping(target = "order", ignore = true) // Sẽ set trong service nếu có orderId
    @Mapping(target = "status", ignore = true) // Mặc định là PENDING
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Review requestToReview(ReviewRequest request);

    // Có thể thêm phương thức update nếu cho phép sửa review
}