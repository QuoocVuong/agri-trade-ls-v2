package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/farmers") // Base path cho API public liên quan đến farmer
@RequiredArgsConstructor
public class PublicFarmerController {

  private final UserService userService; // Inject UserService vì logic nằm ở đó

  /**
   * Endpoint lấy danh sách nông dân nổi bật.
   *
   * @param limit Số lượng tối đa cần lấy (mặc định là 4).
   * @return ResponseEntity chứa ApiResponse với danh sách FarmerSummaryResponse.
   */
  @GetMapping("/featured")
  public ResponseEntity<ApiResponse<List<FarmerSummaryResponse>>> getFeaturedFarmers(
      @RequestParam(name = "limit", defaultValue = "4") int limit) {

    List<FarmerSummaryResponse> featuredFarmers =
        userService.getFeaturedFarmers(Math.max(1, limit)); // Đảm bảo limit >= 1
    return ResponseEntity.ok(ApiResponse.success(featuredFarmers));
  }

  // ===== ENDPOINT  CHO TÌM KIẾM/LỌC =====
  @GetMapping
  public ResponseEntity<ApiResponse<Page<FarmerSummaryResponse>>> searchPublicFarmers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String provinceCode,
      @PageableDefault(size = 12, sort = "farmName,asc")
          Pageable pageable) { // Ví dụ sort theo tên farm

    Page<FarmerSummaryResponse> farmerPage =
        userService.searchPublicFarmers(keyword, provinceCode, pageable);
    return ResponseEntity.ok(ApiResponse.success(farmerPage));
  }
  // =======================================
}
