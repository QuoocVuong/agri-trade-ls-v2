package com.yourcompany.agritrade.usermanagement.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.config.properties.JwtProperties;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.LoginResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritrade.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerSummaryMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;
  private final FarmerProfileRepository farmerProfileRepository; // Inject repo mới
  private final BusinessProfileRepository businessProfileRepository; // Inject repo mới
  private final FarmerProfileMapper farmerProfileMapper; // Inject trực tiếp
  private final BusinessProfileMapper businessProfileMapper; // Inject trực tiếp
  private final EmailService emailService;
  private final FarmerSummaryMapper farmerSummaryMapper;
  private final JwtTokenProvider jwtTokenProvider; // Inject JWT provider

  private final JwtProperties jwtProperties;

  private final NotificationService notificationService;

  private static final String MSG_USER_NOT_FOUND_WITH_EMAIL = "User not found with email: ";

  @Value("${app.frontend.url:http://localhost:4200}") // Lấy URL frontend
  private String frontendUrl;

  @Value("${google.auth.client-id}")
  private String googleClientId;

  private static final int TOKEN_EXPIRY_HOURS = 24; // Thời gian hết hạn token (giờ)

  @Override
  @Transactional
  public UserResponse registerUser(UserRegistrationRequest registrationRequest) { // Đổi tên tham số

    String email = registrationRequest.getEmail().toLowerCase(); // << CHUẨN HÓA EMAIL

    // 1. Kiểm tra trùng lặp
    if (userRepository.existsByEmailIgnoringSoftDelete(email)) {
      throw new BadRequestException("Error: Email is already taken!");
    }
    if (registrationRequest.getPhoneNumber() != null
        && userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber())) {
      throw new BadRequestException("Error: Phone number is already taken!");
    }

    // 2. Tạo User mới
    User user = new User();
    user.setEmail(email); // email đã được toLowerCase()
    user.setProvider("LOCAL"); // << Đặt provider là LOCAL khi đăng ký thủ công
    user.setPasswordHash(passwordEncoder.encode(registrationRequest.getPassword()));
    user.setFullName(registrationRequest.getFullName());
    user.setPhoneNumber(registrationRequest.getPhoneNumber());
    user.setActive(false);

    // Tạo token xác thực
    String token = UUID.randomUUID().toString();
    user.setVerificationToken(token);
    user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));

    // 3. Gán vai trò mặc định (ví dụ: ROLE_CONSUMER)
    Set<Role> roles = new HashSet<>();
    Role userRole =
        roleRepository
            .findByName(RoleType.ROLE_CONSUMER)
            .orElseThrow(
                () -> new ResourceNotFoundException("Role", "name", RoleType.ROLE_CONSUMER.name()));
    roles.add(userRole);
    user.setRoles(roles);

    // 4. Lưu User vào DB
    try {
      User savedUser =
          userRepository.saveAndFlush(
              user); // Dùng saveAndFlush để lỗi DB xảy ra ngay lập tức nếu có

      // Gửi email xác thực (bất đồng bộ)

      String verificationUrl = frontendUrl + "/auth/verify-email?token=" + token;
      emailService.sendVerificationEmail(savedUser, token, verificationUrl);

      // 5. Map sang DTO để trả về

      return userMapper.toUserResponse(savedUser);
    } catch (DataIntegrityViolationException e) {
      throw new BadRequestException("Error: Email is already taken! Please try a different email.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getCurrentUserProfile(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName(); // Lấy email từ principal
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new UsernameNotFoundException(MSG_USER_NOT_FOUND_WITH_EMAIL + email));

    // Lấy thêm thông tin profile nếu có
    UserProfileResponse response = userMapper.toUserProfileResponse(user); // Mapper cơ bản

    // Kiểm tra và lấy profile tương ứng
    if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
      farmerProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  // Sử dụng farmerProfileMapper đã inject
                  response.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile)));
    } else if (user.getRoles().stream()
        .anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
      businessProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  // Sử dụng businessProfileMapper đã inject
                  response.setBusinessProfile(
                      businessProfileMapper.toBusinessProfileResponse(profile)));
    }

    return response;
  }

  @Override
  @Transactional
  public void changePassword(
      Authentication authentication, PasswordChangeRequest passwordChangeRequest) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> new UsernameNotFoundException(MSG_USER_NOT_FOUND_WITH_EMAIL + email));

    // Kiểm tra mật khẩu hiện tại
    if (!passwordEncoder.matches(
        passwordChangeRequest.getCurrentPassword(), user.getPasswordHash())) {
      throw new BadRequestException("Incorrect current password");
    }

    // Mã hóa và cập nhật mật khẩu mới
    user.setPasswordHash(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));

    invalidateRefreshTokenForUser(user.getEmail());

    userRepository.save(user);
  }

  // --- Implement các phương thức cho Admin nếu cần ---

  @Override
  @Transactional
  public UserResponse updateCurrentUserProfile(
      Authentication authentication, UserUpdateRequest updateRequest) {
    User user = getUserFromAuthentication(authentication); // Dùng lại helper method

    // Cập nhật các trường nếu được cung cấp trong request
    if (updateRequest.getFullName() != null && !updateRequest.getFullName().isBlank()) {
      user.setFullName(updateRequest.getFullName());
    }
    if (updateRequest.getPhoneNumber() != null) { // Cho phép cập nhật thành rỗng hoặc số mới
      // Thêm kiểm tra trùng lặp SĐT nếu cần và SĐT không phải của chính user đó
      if (!updateRequest.getPhoneNumber().equals(user.getPhoneNumber())
          && userRepository.existsByPhoneNumber(updateRequest.getPhoneNumber())) {
        throw new BadRequestException("Phone number is already taken.");
      }
      user.setPhoneNumber(
          updateRequest.getPhoneNumber().isBlank() ? null : updateRequest.getPhoneNumber());
    }
    if (updateRequest.getAvatarUrl() != null) {
      user.setAvatarUrl(
          updateRequest.getAvatarUrl().isBlank() ? null : updateRequest.getAvatarUrl());
    }

    User updatedUser = userRepository.save(user);
    return userMapper.toUserResponse(updatedUser);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<UserResponse> getAllUsers(Pageable pageable) {
    // userRepository.findAll(pageable) bây giờ đã tự động lọc is_deleted=false nhờ @Where
    return userRepository.findAll(pageable).map(userMapper::toUserResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getUserProfileById(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

    // Lấy profile tương tự như getCurrentUserProfile
    UserProfileResponse response = userMapper.toUserProfileResponse(user);
    if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
      farmerProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  response.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile)));
    } else if (user.getRoles().stream()
        .anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
      businessProfileRepository
          .findById(user.getId())
          .ifPresent(
              profile ->
                  response.setBusinessProfile(
                      businessProfileMapper.toBusinessProfileResponse(profile)));
    }
    return response;
  }

  @Override
  @Transactional
  public UserResponse updateUserStatus(Long id, boolean isActive) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    user.setActive(isActive);
    User updatedUser = userRepository.save(user);

    // *** Gửi thông báo cho user bị thay đổi trạng thái ***
    notificationService.sendAccountStatusUpdateNotification(
        updatedUser, isActive); // Gọi NotificationService

    return userMapper.toUserResponse(updatedUser);
  }

  @Override
  @Transactional
  public UserResponse updateUserRoles(Long id, Set<RoleType> roleNames) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

    Set<Role> newRoles = new HashSet<>();
    for (RoleType roleName : roleNames) {
      Role role =
          roleRepository
              .findByName(roleName)
              .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName.name()));
      newRoles.add(role);
    }

    user.setRoles(newRoles);
    User updatedUser = userRepository.save(user);

    // *** Gửi thông báo cho user bị thay đổi vai trò ***
    notificationService.sendRolesUpdateNotification(updatedUser); // Gọi NotificationService

    return userMapper.toUserResponse(updatedUser);
  }

  // Helper method (đã có ở Business/Farmer Service Impl)
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException(MSG_USER_NOT_FOUND_WITH_EMAIL + email));
  }

  // (Optional) Thêm phương thức xóa mềm
  // @Override
  @Transactional
  public void softDeleteUser(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    userRepository.delete(user); // Hibernate sẽ chạy câu lệnh trong @SQLDelete
  }

  // (Optional) Thêm phương thức khôi phục user
  @Transactional
  public UserResponse restoreUser(Long id) {
    User user =
        userRepository
            .findByIdIncludingDeleted(id) // Dùng phương thức custom
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    if (!user.isDeleted()) {
      throw new BadRequestException("User is not deleted.");
    }
    user.setDeleted(false);
    User restoredUser = userRepository.save(user);
    return userMapper.toUserResponse(restoredUser);
  }

  @Override
  @Transactional
  public boolean verifyEmail(String token) {
    User user =
        userRepository
            .findByVerificationToken(token) // Cần thêm phương thức này vào Repo
            .orElseThrow(() -> new BadRequestException("Invalid verification token."));

    if (user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
      // Có thể tạo lại token và gửi lại mail hoặc báo lỗi hết hạn
      // Ví dụ: Xóa token cũ để user có thể yêu cầu gửi lại
      user.setVerificationToken(null);
      user.setVerificationTokenExpiry(null);
      userRepository.save(user);
      throw new BadRequestException("Verification token has expired.");
    }

    user.setActive(true);
    user.setVerificationToken(null); // Xóa token sau khi đã dùng
    user.setVerificationTokenExpiry(null);
    userRepository.save(user);

    // *** Gửi thông báo Welcome sau khi xác thực thành công ***
    notificationService.sendWelcomeNotification(user); // Gọi NotificationService

    return true;
  }

  @Override
  @Transactional
  public void initiatePasswordReset(String email) {
    Optional<User> userOptional = userRepository.findByEmail(email); // Tìm cả user inactive

    if (userOptional.isPresent()) {
      User user = userOptional.get();
      String token = UUID.randomUUID().toString();
      user.setVerificationToken(token); // Sử dụng lại trường token
      user.setVerificationTokenExpiry(
          LocalDateTime.now().plusHours(1)); // Reset token thường hết hạn nhanh hơn (1 giờ)
      userRepository.save(user);

      String resetUrl = frontendUrl + "/auth/reset-password?token=" + token;
      emailService.sendPasswordResetEmail(user, token, resetUrl);
    } else {
      // Không báo lỗi để tránh lộ email tồn tại, nhưng có thể log lại
      log.warn("Password reset requested for non-existent email: {}", email);
    }
    // Luôn trả về thành công cho client
  }

  @Override
  @Transactional
  public void resetPassword(String token, String newPassword) {
    User user =
        userRepository
            .findByVerificationToken(token) // Tìm bằng token
            .orElseThrow(() -> new BadRequestException("Invalid password reset token."));

    if (user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
      user.setVerificationToken(null);
      user.setVerificationTokenExpiry(null);
      userRepository.save(user);
      throw new BadRequestException("Password reset token has expired.");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setVerificationToken(null); // Xóa token
    user.setVerificationTokenExpiry(null);
    userRepository.save(user);

    // *** Gửi thông báo đổi mật khẩu thành công ***
    notificationService.sendPasswordChangedNotification(user); // Gọi NotificationService

    // Có thể gửi email thông báo đổi mật khẩu thành công (tùy chọn)
  }

  // ===== IMPLEMENT PHƯƠNG THỨC MỚI =====
  @Override
  @Transactional(readOnly = true)
  public List<FarmerSummaryResponse> getFeaturedFarmers(int limit) {
    log.debug("Fetching top {} featured farmers based on follower count.", limit);

    // Tạo Pageable để giới hạn kết quả trả về từ repository
    Pageable pageable = PageRequest.of(0, limit); // Lấy trang đầu tiên, giới hạn số lượng

    // Gọi repository để lấy danh sách User là Farmer, đã sắp xếp theo followerCount
    List<User> topFarmers =
        userRepository.findTopByRoles_NameOrderByFollowerCountDesc(RoleType.ROLE_FARMER, pageable);

    // Map danh sách User sang FarmerSummaryResponse
    // Cần lấy thêm FarmerProfile cho mỗi User để có farmName và provinceCode
    List<FarmerSummaryResponse> responseList = new ArrayList<>();
    for (User farmerUser : topFarmers) {
      // Tìm FarmerProfile tương ứng
      Optional<FarmerProfile> profileOpt = farmerProfileRepository.findById(farmerUser.getId());
      // Sử dụng mapper để tạo DTO, xử lý trường hợp profile không tồn tại
      FarmerSummaryResponse summaryDto =
          profileOpt
              .map(
                  profile ->
                      farmerSummaryMapper.toFarmerSummaryResponse(
                          farmerUser, profile)) // Dùng mapper có cả User và Profile
              .orElseGet(
                  () ->
                      farmerSummaryMapper.userToFarmerSummaryResponse(
                          farmerUser)); // Dùng mapper chỉ có User nếu không có profile
      responseList.add(summaryDto);
    }

    log.info("Found {} featured farmers.", responseList.size());
    return responseList;
  }

  // =====================================

  // ===== IMPLEMENT PHƯƠNG THỨC MỚI =====
  @Override
  @Transactional(readOnly = true)
  public Page<FarmerSummaryResponse> searchPublicFarmers(
      String keyword, String provinceCode, Pageable pageable) {
    log.debug(
        "Searching public farmers with keyword: '{}', provinceCode: {}, pageable: {}",
        keyword,
        provinceCode,
        pageable);

    // *** Tạo Specification cho FarmerProfile ***
    Specification<FarmerProfile> spec =
        Specification.where(isVerified()) // Lọc profile đã VERIFIED
            .and(hasKeywordInProfileOrUser(keyword)) // Lọc theo keyword
            .and(hasProvince(provinceCode)); // Lọc theo tỉnh

    // *** Gọi findAll trên FarmerProfileRepository ***
    Page<FarmerProfile> farmerProfilePage = farmerProfileRepository.findAll(spec, pageable);

    // Map sang Page<FarmerSummaryResponse> và xử lý null bên trong map
    return farmerProfilePage.map(
        profile -> {
          User user = profile.getUser();
          if (user == null) {
            // Trường hợp này rất hiếm nếu DB đúng, nhưng cần xử lý
            log.error(
                "User is null for FarmerProfile with userId: {}. Returning empty summary.",
                profile.getUserId());
            // Trả về một đối tượng rỗng hoặc một giá trị mặc định thay vì null
            // để tránh lỗi filter sau này và giữ đúng cấu trúc Page
            return new FarmerSummaryResponse(
                profile.getUserId(),
                profile.getFarmName(),
                "[User not found]",
                null,
                profile.getProvinceCode(),
                0); // Ví dụ
          }
          // Gọi mapper để tạo DTO hoàn chỉnh
          return farmerSummaryMapper.toFarmerSummaryResponse(user, profile);
        }); // <-- Bỏ .filter(Objects::nonNull) ở đây
  }

  // =====================================

  // --- Specification Helper Methods ---
  private Specification<FarmerProfile> isVerified() {
    return (root, query, cb) ->
        cb.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED);
  }

  private Specification<FarmerProfile> hasKeywordInProfileOrUser(String keyword) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(keyword)) {
        return cb.conjunction();
      }
      String pattern = "%" + keyword.toLowerCase() + "%";
      // Join với User từ FarmerProfile
      Join<FarmerProfile, User> userJoin =
          root.join("user", JoinType.INNER); // INNER JOIN vì farmer phải có user

      Predicate farmNameLike = cb.like(cb.lower(root.get("farmName")), pattern);
      Predicate userFullNameLike = cb.like(cb.lower(userJoin.get("fullName")), pattern);
      // Predicate userEmailLike = cb.like(cb.lower(userJoin.get("email")), pattern); // Thêm nếu
      // muốn tìm theo email

      return cb.or(farmNameLike, userFullNameLike /*, userEmailLike */);
    };
  }

  private Specification<User> hasKeywordInUserOrProfile(String keyword) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(keyword)) {
        return cb.conjunction(); // Không lọc nếu keyword rỗng
      }
      String pattern = "%" + keyword.toLowerCase() + "%";
      // Join với farmer_profiles (LEFT JOIN để vẫn lấy user dù chưa có profile)
      Join<User, FarmerProfile> profileJoin = root.join("farmerProfile", JoinType.LEFT);

      // Tạo các điều kiện LIKE
      Predicate nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
      Predicate farmNameLike = cb.like(cb.lower(profileJoin.get("farmName")), pattern);


      // Kết hợp bằng OR
      return cb.or(nameLike, farmNameLike /*, descriptionLike */);
    };
  }

  private Specification<FarmerProfile> hasProvince(String provinceCode) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(provinceCode)) {
        return cb.conjunction();
      }
      return cb.equal(root.get("provinceCode"), provinceCode);
    };
  }

  @Override
  @Transactional
  public LoginResponse processLoginAuthentication(Authentication authentication) {
    User user =
        userRepository
            .findByEmail(authentication.getName())
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found after authentication"));

    String accessToken = jwtTokenProvider.generateAccessToken(authentication);
    String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

    user.setRefreshToken(refreshToken);
    // Lấy thời gian hết hạn từ jwtProperties
    long refreshTokenDurationMs = jwtProperties.getRefreshToken().getExpirationMs();
    user.setRefreshTokenExpiryDate(
        LocalDateTime.now().plusNanos(TimeUnit.MILLISECONDS.toNanos(refreshTokenDurationMs)));
    userRepository.save(user);

    UserResponse userResponse = userMapper.toUserResponse(user);
    return new LoginResponse(accessToken, refreshToken, userResponse);
  }

  @Override
  @Transactional
  public LoginResponse processGoogleLogin(String googleIdTokenString)
      throws GeneralSecurityException, IOException {
    // 1. Xác thực Google ID Token
    GoogleIdTokenVerifier verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
            .setAudience(Collections.singletonList(googleClientId))
            // Hoặc nếu bạn có nhiều client ID: .setAudience(Arrays.asList(CLIENT_ID_1, CLIENT_ID_2,
            // ...))
            // .setIssuer("https://accounts.google.com") // Có thể chỉ định issuer
            .build();

    GoogleIdToken idToken = verifier.verify(googleIdTokenString);
    if (idToken == null) {
      throw new BadRequestException("Invalid Google ID Token.");
    }

    // 2. Lấy thông tin user từ Payload
    GoogleIdToken.Payload payload = idToken.getPayload();
    String userId = payload.getSubject(); // Google User ID (providerId)
    String email = payload.getEmail();
    boolean emailVerified = payload.getEmailVerified();
    String name = (String) payload.get("name");
    String pictureUrl = (String) payload.get("picture");
    // String locale = (String) payload.get("locale");
    // String familyName = (String) payload.get("family_name");
    // String givenName = (String) payload.get("given_name");

    if (!emailVerified) {
      throw new BadRequestException("Google account email is not verified.");
    }
    if (email == null) {
      throw new BadRequestException("Could not retrieve email from Google token.");
    }

    // 3. Tìm hoặc tạo User trong DB
    User user = findOrCreateUserForOAuth2(email, name, pictureUrl, "GOOGLE", userId);

    // 4. Tạo JWT cho user của bạn
    // Cần tạo đối tượng Authentication thủ công để generateToken
    // Lấy authorities từ user đã tìm/tạo
    Collection<? extends GrantedAuthority> authorities =
        mapRolesToAuthorities(user.getRoles()); // Giả sử có hàm này
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            user.getEmail(), null, mapRolesToAuthorities(user.getRoles()));

    String accessToken = jwtTokenProvider.generateAccessToken(authentication);
    String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

    user.setRefreshToken(refreshToken);
    long refreshTokenDurationMs = jwtProperties.getRefreshToken().getExpirationMs();
    user.setRefreshTokenExpiryDate(
        LocalDateTime.now().plusNanos(TimeUnit.MILLISECONDS.toNanos(refreshTokenDurationMs)));
    userRepository.save(user);

    UserResponse userResponse = userMapper.toUserResponse(user);
    return new LoginResponse(accessToken, refreshToken, userResponse);
  }

  // Hàm helper tìm hoặc tạo user
  private User findOrCreateUserForOAuth2(String email, String fullName, String avatarUrl, String googleProviderName, String googleProviderId) {
    // Luôn chuẩn hóa email về chữ thường để đảm bảo tính nhất quán khi truy vấn
    String normalizedEmail = email.toLowerCase();

    // Tìm kiếm user trong DB bằng email đã chuẩn hóa
    Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

    if (userOptional.isPresent()) {
      // Kịch bản: User đã tồn tại trong DB
      User existingUser = userOptional.get();
      String currentProvider = existingUser.getProvider(); // Lấy provider hiện tại của user

      // Kiểm tra xem user hiện tại có phải là user GOOGLE, LOCAL hoặc chưa có provider (null) không.
      // Điều này cho phép liên kết tài khoản Google với tài khoản LOCAL đã có,
      // hoặc đăng nhập lại nếu đã từng đăng nhập bằng Google.
      if (googleProviderName.equals(currentProvider) || currentProvider == null || "LOCAL".equalsIgnoreCase(currentProvider)) {

        log.info("User {} found. Current provider: {}. Attempting to login/link with provider: {}", normalizedEmail, currentProvider, googleProviderName);

        boolean needsUpdate = false; // Cờ để theo dõi xem có cần lưu lại user không

        // Nếu provider hiện tại là LOCAL hoặc null, cập nhật thành GOOGLE và lưu providerId
        if (currentProvider == null || "LOCAL".equalsIgnoreCase(currentProvider)) {
          existingUser.setProvider(googleProviderName);
          existingUser.setProviderId(googleProviderId);
          needsUpdate = true;
          log.debug("Linking Google account to existing LOCAL/null provider user: {}", normalizedEmail);
        } else if (googleProviderId != null && !googleProviderId.equals(existingUser.getProviderId())) {
          // Trường hợp hiếm: providerId từ Google thay đổi cho cùng một email. Cập nhật nếu cần.
          existingUser.setProviderId(googleProviderId);
          needsUpdate = true;
          log.warn("Google providerId for user {} changed from {} to {}. Updating.", normalizedEmail, existingUser.getProviderId(), googleProviderId);
        }

        // Cập nhật thông tin cá nhân (tên, avatar) nếu có từ Google và khác với thông tin hiện tại
        if (fullName != null && !fullName.equals(existingUser.getFullName())) {
          existingUser.setFullName(fullName);
          needsUpdate = true;
        }
        if (avatarUrl != null && !avatarUrl.equals(existingUser.getAvatarUrl())) {
          existingUser.setAvatarUrl(avatarUrl);
          needsUpdate = true;
        }

        // Đảm bảo tài khoản được active (vì Google đã xác thực email)
        // và xóa token xác thực email (nếu có) vì không còn cần thiết
        if (!existingUser.isActive()) {
          existingUser.setActive(true);
          existingUser.setVerificationToken(null);
          existingUser.setVerificationTokenExpiry(null);
          needsUpdate = true;
          log.debug("Activating user {} and clearing verification token due to Google Sign-In.", normalizedEmail);
        }

        // Nếu có bất kỳ thay đổi nào, lưu lại user vào DB
        if (needsUpdate) {
          try {
            log.debug("Attempting to save updated existing user: {}", normalizedEmail);
            return userRepository.save(existingUser);
          } catch (DataIntegrityViolationException dive) {
            // Xử lý lỗi nếu có vi phạm ràng buộc dữ liệu khi cập nhật
            log.error("Data integrity violation while updating existing OAuth2 user {}: {}. Root cause: {}",
                    normalizedEmail, dive.getMessage(), dive.getRootCause() != null ? dive.getRootCause().getMessage() : "N/A", dive);
            throw new BadRequestException("Error updating user data. Possible data conflict: " +
                    (dive.getRootCause() != null ? dive.getRootCause().getMessage() : dive.getMessage()));
          } catch (Exception e) {
            // Xử lý các lỗi không mong muốn khác
            log.error("Unexpected error updating existing OAuth2 user {}: {}", normalizedEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to update user during OAuth2 process: " + e.getMessage(), e);
          }
        }
        // Nếu không có gì cần cập nhật, trả về user hiện tại
        return existingUser;

      } else {
        // User đã tồn tại nhưng với một provider OAuth2 khác (ví dụ: Facebook)
        // và không phải là LOCAL hoặc null. Không cho phép liên kết tự động.
        log.warn("User with email {} already exists with provider {} (expected {} or LOCAL/null). Cannot link Google account.",
                normalizedEmail, currentProvider, googleProviderName);
        throw new BadRequestException(
                "Tài khoản với email này đã được đăng ký bằng một phương thức khác (" + currentProvider
                        + ") không thể tự động liên kết với Google."
        );
      }
    } else {
      // Kịch bản: User chưa tồn tại trong DB, tạo user mới
      log.info("User with email {} not found. Creating new user from OAuth2 provider {}.", normalizedEmail, googleProviderName);
      User newUser = new User();
      newUser.setEmail(normalizedEmail);
      newUser.setFullName(fullName != null ? fullName : "Người dùng " + googleProviderName); // Tên mặc định nếu không có
      newUser.setAvatarUrl(avatarUrl);
      // Tạo mật khẩu ngẫu nhiên vì user OAuth2 không dùng mật khẩu của ứng dụng
      newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
      newUser.setActive(true); // Tài khoản được kích hoạt ngay vì email đã được Google xác thực
      newUser.setProvider(googleProviderName); // Đặt provider là "GOOGLE"
      newUser.setProviderId(googleProviderId); // Lưu ID từ Google

      // Không cần token xác thực email của ứng dụng vì đã xác thực qua Google
      newUser.setVerificationToken(null);
      newUser.setVerificationTokenExpiry(null);

      // Khởi tạo các trường count nếu cần (mặc dù Entity User đã có giá trị mặc định là 0)
      // newUser.setFollowerCount(0);
      // newUser.setFollowingCount(0);

      try {
        // Gán vai trò mặc định cho user mới (ví dụ: ROLE_CONSUMER)
        Role defaultRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                .orElseThrow(() -> {
                  // Lỗi nghiêm trọng nếu role mặc định không được cấu hình trong DB
                  log.error("Default role {} not found in database. Cannot create new OAuth2 user.", RoleType.ROLE_CONSUMER.name());
                  // Ném ResourceNotFoundException để controller có thể bắt và trả về lỗi 500 hoặc 400 phù hợp
                  return new ResourceNotFoundException("Role", "name", RoleType.ROLE_CONSUMER.name());
                });

        // Sử dụng một Set có thể thay đổi (mutable) như HashSet
        // để tránh UnsupportedOperationException khi Hibernate quản lý collection
        Set<Role> roles = new HashSet<>();
        roles.add(defaultRole);
        newUser.setRoles(roles);

        log.info("Attempting to save new user from OAuth2 provider {}: {}", googleProviderName, normalizedEmail);
        return userRepository.save(newUser);
      } catch (ResourceNotFoundException rnfe) {
        // Xử lý lỗi nếu không tìm thấy role mặc định
        log.error("Failed to create new OAuth2 user {} due to missing default role: {}", normalizedEmail, rnfe.getMessage(), rnfe);
        // Ném lại RuntimeException để báo hiệu lỗi cấu hình server
        throw new RuntimeException("Server configuration error: " + rnfe.getMessage(), rnfe);
      } catch (DataIntegrityViolationException dive) {
        // Xử lý lỗi nếu có vi phạm ràng buộc dữ liệu khi tạo user mới
        // (ví dụ: một trường unique khác ngoài email bị trùng, dù ít khả năng xảy ra ở đây nếu logic đúng)
        log.error("Data integrity violation while creating new OAuth2 user {}: {}. Root cause: {}",
                normalizedEmail, dive.getMessage(), dive.getRootCause() != null ? dive.getRootCause().getMessage() : "N/A", dive);
        throw new BadRequestException("Error creating user. Possible duplicate entry or data conflict: " +
                (dive.getRootCause() != null ? dive.getRootCause().getMessage() : dive.getMessage()));
      } catch (Exception e) {
        // Xử lý các lỗi không mong muốn khác
        log.error("Unexpected error creating new OAuth2 user {}: {}", normalizedEmail, e.getMessage(), e);
        throw new RuntimeException("Failed to create user during OAuth2 process: " + e.getMessage(), e);
      }
    }
  }

  // Hàm helper map roles (cần có trong class này hoặc UserDetailsServiceImpl)
  private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Set<Role> roles) {
    return roles.stream()
        .map(role -> new SimpleGrantedAuthority(role.getName().name()))
        .collect(Collectors.toList());
  }

  // Phương thức làm mới token
  @Transactional
  public LoginResponse refreshToken(
      String refreshTokenRequest) { // refreshTokenRequest là chuỗi refresh token từ client
    // 1. Validate refresh token (cấu trúc, chữ ký, chưa hết hạn theo claim 'exp')
    if (!jwtTokenProvider.validateToken(refreshTokenRequest)) {
      log.warn(
          "Attempt to refresh with an invalid (malformed, expired by claim, or bad signature) refresh token.");
      throw new BadRequestException("Invalid Refresh Token");
    }

    // 2. Lấy email (hoặc subject) từ refresh token
    String email = jwtTokenProvider.getEmailFromToken(refreshTokenRequest);
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () -> {
                  log.warn("User not found for refresh token with email: {}", email);
                  return new UsernameNotFoundException(
                      "User not found with email from refresh token: " + email);
                });

    // 3. Kiểm tra refresh token trong DB với token được gửi lên và thời gian hết hạn trong DB
    if (user.getRefreshToken() == null
        || !user.getRefreshToken().equals(refreshTokenRequest)
        || // So sánh token trong DB với token client gửi
        user.getRefreshTokenExpiryDate() == null
        || user.getRefreshTokenExpiryDate()
            .isBefore(LocalDateTime.now())) { // Kiểm tra thời gian hết hạn trong DB

      log.warn(
          "Refresh token for user {} is invalid, not matching DB, or expired in DB. Forcing re-login.",
          email);
      // (Tùy chọn) Thu hồi tất cả refresh token của user này nếu phát hiện lạm dụng hoặc token cũ
       user.setRefreshToken(null);
       user.setRefreshTokenExpiryDate(null);
       userRepository.save(user);
      throw new BadRequestException("Refresh token is invalid or expired. Please login again.");
    }

    // 4. Nếu mọi thứ ổn, tạo access token mới
    // Tạo Authentication object mới từ User để generate access token
    Collection<? extends GrantedAuthority> authorities = mapRolesToAuthorities(user.getRoles());
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
    String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);

    // 5. (QUAN TRỌNG - Chiến lược Refresh Token)
    // Lựa chọn 1: Trả về access token mới và giữ nguyên refresh token cũ (nếu nó còn hạn dài)
    // LoginResponse response = new LoginResponse(newAccessToken, refreshTokenRequest,
    // userMapper.toUserResponse(user));

    // Lựa chọn 2: Tạo cả access token MỚI và refresh token MỚI (xoay vòng refresh token - an toàn
    // hơn)
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);
    user.setRefreshToken(newRefreshToken);
    user.setRefreshTokenExpiryDate(
        LocalDateTime.now().plusSeconds(jwtProperties.getRefreshToken().getExpirationMs() / 1000));
    userRepository.save(user);
    LoginResponse response =
        new LoginResponse(newAccessToken, newRefreshToken, userMapper.toUserResponse(user));
    log.info("Refreshed token for user {}. New access and refresh tokens generated.", email);

    return response;
  }

  @Override
  @Transactional
  public void invalidateRefreshTokenForUser(String email) {
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              user.setRefreshToken(null);
              user.setRefreshTokenExpiryDate(null);
              userRepository.save(user);
              log.info("Invalidated refresh token for user: {}", email);
            });
  }
}
