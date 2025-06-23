package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface UserService {
  UserResponse registerUser(UserRegistrationRequest registrationRequest);

  // Lấy thông tin profile đầy đủ của user đang đăng nhập
  UserProfileResponse getCurrentUserProfile(Authentication authentication);

  // Cập nhật mật khẩu

  // Cập nhật thông tin cơ bản của user đang đăng nhập
  UserResponse updateCurrentUserProfile(
      Authentication authentication, UserUpdateRequest updateRequest);

  void changePassword(Authentication authentication, PasswordChangeRequest passwordChangeRequest);

  /**
   * Xác thực email của người dùng bằng token.
   *
   * @param token Mã xác thực từ email.
   * @return true nếu xác thực thành công. // * @throws BadRequestException nếu token không hợp lệ
   *     hoặc hết hạn.
   */
  boolean verifyEmail(String token);

  /**
   * Bắt đầu quá trình quên mật khẩu cho email được cung cấp. Gửi email chứa link reset nếu email
   * tồn tại.
   *
   * @param email Email của người dùng quên mật khẩu.
   */
  void initiatePasswordReset(String email);

  /**
   * Hoàn tất việc reset mật khẩu bằng token và mật khẩu mới.
   *
   * @param token Mã reset từ email.
   * @param newPassword Mật khẩu mới. //* @throws BadRequestException nếu token không hợp lệ hoặc
   *     hết hạn.
   */
  void resetPassword(String token, String newPassword);

  // --- Các phương thức cho Admin ---
  Page<UserResponse> getAllUsers(Pageable pageable);

  UserProfileResponse getUserProfileById(Long id); // Lấy profile đầy đủ theo ID

  UserResponse updateUserStatus(Long id, boolean isActive);

  UserResponse updateUserRoles(Long id, Set<RoleType> roles);

  /**
   * Lấy danh sách nông dân nổi bật (ví dụ: theo số người theo dõi).
   *
   * @param limit Số lượng tối đa cần lấy.
   * @return Danh sách FarmerSummaryResponse.
   */
  List<FarmerSummaryResponse> getFeaturedFarmers(int limit);

  /**
   * Tìm kiếm và lọc danh sách Farmer công khai (đã được duyệt).
   *
   * @param keyword Từ khóa tìm kiếm (tên user, tên trang trại).
   * @param provinceCode Mã tỉnh để lọc.
   * @param pageable Thông tin phân trang và sắp xếp.
   * @return Trang chứa FarmerSummaryResponse.
   */
  Page<FarmerSummaryResponse> searchPublicFarmers(
      String keyword, String provinceCode, Pageable pageable);

  // --- PHƯƠNG THỨC CHO XỬ LÝ LOGIN VÀ TOKEN ---
  /**
   * Xử lý việc xác thực thành công, tạo tokens và lưu refresh token.
   *
   * @param authentication Đối tượng Authentication đã được xác thực.
   * @return LoginResponse chứa accessToken, refreshToken và thông tin user.
   */
  LoginResponse processLoginAuthentication(Authentication authentication);

  /**
   * Xử lý đăng nhập bằng Google ID Token. Xác thực token, tìm hoặc tạo người dùng, và trả về JWT
   * của ứng dụng.
   *
   * @param idTokenString Google ID Token nhận được từ Frontend.
   * @return JWT của ứng dụng AgriTradeLS cho người dùng đã xác thực.
   */
  LoginResponse processGoogleLogin(String idTokenString)
      throws GeneralSecurityException, IOException;

  /**
   * Làm mới access token bằng cách sử dụng refresh token.
   *
   * @param refreshTokenRequest Chuỗi refresh token từ client.
   * @return LoginResponse chứa accessToken mới và refresh token (có thể là cũ hoặc mới tùy chiến
   *     lược). //* @throws BadRequestException Nếu refresh token không hợp lệ hoặc hết hạn.
   */
  LoginResponse refreshToken(String refreshTokenRequest);

  void invalidateRefreshTokenForUser(String email);

  Page<UserResponse> searchBuyers(String keyword, Pageable pageable);
}
