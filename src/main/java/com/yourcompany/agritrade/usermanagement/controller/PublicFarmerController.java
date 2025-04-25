package com.yourcompany.agritrade.usermanagement.controller; // Hoặc package public

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.service.UserService; // Inject UserService
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/farmers") // Base path cho API public liên quan đến farmer
@RequiredArgsConstructor
public class PublicFarmerController {

    private final UserService userService; // Inject UserService vì logic nằm ở đó

    /**
     * Endpoint lấy danh sách nông dân nổi bật.
     * @param limit Số lượng tối đa cần lấy (mặc định là 4).
     * @return ResponseEntity chứa ApiResponse với danh sách FarmerSummaryResponse.
     */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<FarmerSummaryResponse>>> getFeaturedFarmers(
            @RequestParam(name = "limit", defaultValue = "4") int limit) {

        List<FarmerSummaryResponse> featuredFarmers = userService.getFeaturedFarmers(Math.max(1, limit)); // Đảm bảo limit >= 1
        return ResponseEntity.ok(ApiResponse.success(featuredFarmers));
    }

    // Có thể thêm các endpoint public khác liên quan đến farmer ở đây
    // Ví dụ: API tìm kiếm/lọc farmer công khai
    /*
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FarmerSummaryResponse>>> searchPublicFarmers(...) {
        // ...
    }
    */
}