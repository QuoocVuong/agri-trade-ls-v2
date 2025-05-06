package com.yourcompany.agritrade.usermanagement.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
// Cập nhật import cho DTOs
import com.yourcompany.agritrade.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritrade.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    private final NotificationService notificationService;
    @Value("${app.frontend.url:http://localhost:4200}") // Lấy URL frontend
    private String frontendUrl;

    @Value("${google.auth.client-id}")
    private String googleClientId;

    private static final int TOKEN_EXPIRY_HOURS = 24; // Thời gian hết hạn token (giờ)


    @Override
    @Transactional
    public UserResponse registerUser(UserRegistrationRequest registrationRequest) { // Đổi tên tham số
        // 1. Kiểm tra trùng lặp
        if (userRepository.existsByEmailIgnoringSoftDelete(registrationRequest.getEmail())) {
            throw new BadRequestException("Error: Email is already taken!");
        }
        if (registrationRequest.getPhoneNumber() != null && userRepository.existsByPhoneNumber(registrationRequest.getPhoneNumber())) {
            throw new BadRequestException("Error: Phone number is already taken!");
        }

        // 2. Tạo User mới
        User user = new User();
        user.setEmail(registrationRequest.getEmail());
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
        Role userRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", RoleType.ROLE_CONSUMER.name()));
        roles.add(userRole);
        user.setRoles(roles);

        // 4. Lưu User vào DB
        User savedUser = userRepository.save(user);

        // Gửi email xác thực (bất đồng bộ)
        String verificationUrl = frontendUrl + "/auth/verify-email?token=" + token; // Hoặc /api/auth/verify?token=
        emailService.sendVerificationEmail(savedUser, token, verificationUrl);


        // 5. Map sang DTO để trả về
        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName(); // Lấy email từ principal
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Lấy thêm thông tin profile nếu có
        UserProfileResponse response = userMapper.toUserProfileResponse(user); // Mapper cơ bản

        // Kiểm tra và lấy profile tương ứng
        if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
            farmerProfileRepository.findById(user.getId()).ifPresent(profile ->
                    // Sử dụng farmerProfileMapper đã inject
                    response.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile))
            );
        } else if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
            businessProfileRepository.findById(user.getId()).ifPresent(profile ->
                    // Sử dụng businessProfileMapper đã inject
                    response.setBusinessProfile(businessProfileMapper.toBusinessProfileResponse(profile))
            );
        }

        return response;
    }

    @Override
    @Transactional
    public void changePassword(Authentication authentication, PasswordChangeRequest passwordChangeRequest) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(passwordChangeRequest.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Incorrect current password");
        }

        // Mã hóa và cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));
        userRepository.save(user);
    }

    // --- Implement các phương thức cho Admin nếu cần ---
    // Ví dụ:
    // @Override
    // public Page<UserResponse> getAllUsers(Pageable pageable) {
    //     return userRepository.findAll(pageable).map(userMapper::toUserResponse);
    // }
    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(Authentication authentication, UserUpdateRequest updateRequest) {
        User user = getUserFromAuthentication(authentication); // Dùng lại helper method

        // Cập nhật các trường nếu được cung cấp trong request
        if (updateRequest.getFullName() != null && !updateRequest.getFullName().isBlank()) {
            user.setFullName(updateRequest.getFullName());
        }
        if (updateRequest.getPhoneNumber() != null) { // Cho phép cập nhật thành rỗng hoặc số mới
            // Thêm kiểm tra trùng lặp SĐT nếu cần và SĐT không phải của chính user đó
            if (!updateRequest.getPhoneNumber().equals(user.getPhoneNumber()) &&
                    userRepository.existsByPhoneNumber(updateRequest.getPhoneNumber())) {
                throw new BadRequestException("Phone number is already taken.");
            }
            user.setPhoneNumber(updateRequest.getPhoneNumber().isBlank() ? null : updateRequest.getPhoneNumber());
        }
        if (updateRequest.getAvatarUrl() != null) {
            user.setAvatarUrl(updateRequest.getAvatarUrl().isBlank() ? null : updateRequest.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        // userRepository.findAll(pageable) bây giờ đã tự động lọc is_deleted=false nhờ @Where
        return userRepository.findAll(pageable)
                .map(userMapper::toUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Lấy profile tương tự như getCurrentUserProfile
        UserProfileResponse response = userMapper.toUserProfileResponse(user);
        if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_FARMER)) {
            farmerProfileRepository.findById(user.getId()).ifPresent(profile ->
                    response.setFarmerProfile(farmerProfileMapper.toFarmerProfileResponse(profile))
            );
        } else if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_BUSINESS_BUYER)) {
            businessProfileRepository.findById(user.getId()).ifPresent(profile ->
                    response.setBusinessProfile(businessProfileMapper.toBusinessProfileResponse(profile))
            );
        }
        return response;
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(isActive);
        User updatedUser = userRepository.save(user);

        // *** Gửi thông báo cho user bị thay đổi trạng thái ***
        notificationService.sendAccountStatusUpdateNotification(updatedUser, isActive); // Gọi NotificationService

        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(Long id, Set<RoleType> roleNames) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        Set<Role> newRoles = new HashSet<>();
        for (RoleType roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
    // (Optional) Thêm phương thức xóa mềm
     //@Override
     @Transactional
     public void softDeleteUser(Long id) {
         User user = userRepository.findById(id)
                 .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
         userRepository.delete(user); // Hibernate sẽ chạy câu lệnh trong @SQLDelete
     }

    // (Optional) Thêm phương thức khôi phục user
     @Transactional
     public UserResponse restoreUser(Long id) {
         User user = userRepository.findByIdIncludingDeleted(id) // Dùng phương thức custom
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
        User user = userRepository.findByVerificationToken(token) // Cần thêm phương thức này vào Repo
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
            user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1)); // Reset token thường hết hạn nhanh hơn (1 giờ)
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
        User user = userRepository.findByVerificationToken(token) // Tìm bằng token
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
        List<User> topFarmers = userRepository.findTopByRoles_NameOrderByFollowerCountDesc(RoleType.ROLE_FARMER, pageable);

        // Map danh sách User sang FarmerSummaryResponse
        // Cần lấy thêm FarmerProfile cho mỗi User để có farmName và provinceCode
        List<FarmerSummaryResponse> responseList = new ArrayList<>();
        for (User farmerUser : topFarmers) {
            // Tìm FarmerProfile tương ứng
            Optional<FarmerProfile> profileOpt = farmerProfileRepository.findById(farmerUser.getId());
            // Sử dụng mapper để tạo DTO, xử lý trường hợp profile không tồn tại
            FarmerSummaryResponse summaryDto = profileOpt
                    .map(profile -> farmerSummaryMapper.toFarmerSummaryResponse(farmerUser, profile)) // Dùng mapper có cả User và Profile
                    .orElseGet(() -> farmerSummaryMapper.userToFarmerSummaryResponse(farmerUser)); // Dùng mapper chỉ có User nếu không có profile
            responseList.add(summaryDto);
        }

        log.info("Found {} featured farmers.", responseList.size());
        return responseList;
    }
    // =====================================


    // ===== IMPLEMENT PHƯƠNG THỨC MỚI =====
    @Override
    @Transactional(readOnly = true)
    public Page<FarmerSummaryResponse> searchPublicFarmers(String keyword, String provinceCode, Pageable pageable) {
        log.debug("Searching public farmers with keyword: '{}', provinceCode: {}, pageable: {}", keyword, provinceCode, pageable);

        // *** Tạo Specification cho FarmerProfile ***
        Specification<FarmerProfile> spec = Specification.where(isVerified()) // Lọc profile đã VERIFIED
                .and(hasKeywordInProfileOrUser(keyword)) // Lọc theo keyword
                .and(hasProvince(provinceCode)); // Lọc theo tỉnh

        // *** Gọi findAll trên FarmerProfileRepository ***
        Page<FarmerProfile> farmerProfilePage = farmerProfileRepository.findAll(spec, pageable);

        // Map sang Page<FarmerSummaryResponse> và xử lý null bên trong map
        return farmerProfilePage.map(profile -> {
            User user = profile.getUser();
            if (user == null) {
                // Trường hợp này rất hiếm nếu DB đúng, nhưng cần xử lý
                log.error("User is null for FarmerProfile with userId: {}. Returning empty summary.", profile.getUserId());
                // Trả về một đối tượng rỗng hoặc một giá trị mặc định thay vì null
                // để tránh lỗi filter sau này và giữ đúng cấu trúc Page
                return new FarmerSummaryResponse(profile.getUserId(), profile.getFarmName(), "[User not found]", null, profile.getProvinceCode(), 0); // Ví dụ
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
            Join<FarmerProfile, User> userJoin = root.join("user", JoinType.INNER); // INNER JOIN vì farmer phải có user

            Predicate farmNameLike = cb.like(cb.lower(root.get("farmName")), pattern);
            Predicate userFullNameLike = cb.like(cb.lower(userJoin.get("fullName")), pattern);
            // Predicate userEmailLike = cb.like(cb.lower(userJoin.get("email")), pattern); // Thêm nếu muốn tìm theo email

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
            // Predicate descriptionLike = cb.like(cb.lower(profileJoin.get("description")), pattern); // Thêm nếu muốn tìm trong mô tả

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
    public String processGoogleLogin(String googleIdTokenString) throws GeneralSecurityException, IOException {
        // 1. Xác thực Google ID Token
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                // Hoặc nếu bạn có nhiều client ID: .setAudience(Arrays.asList(CLIENT_ID_1, CLIENT_ID_2, ...))
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
        Collection<? extends GrantedAuthority> authorities = mapRolesToAuthorities(user.getRoles()); // Giả sử có hàm này
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                authorities);

        String jwt = jwtTokenProvider.generateToken(authentication);

        return jwt; // Trả về JWT
    }

    // Hàm helper tìm hoặc tạo user
    private User findOrCreateUserForOAuth2(String email, String fullName, String avatarUrl, String provider, String providerId) {
        Optional<User> userOptional = userRepository.findByEmail(email); // Tìm theo email

        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();
            // Kiểm tra xem user này có phải từ Google không
            if (!provider.equals(existingUser.getProvider())) {
                // User đã tồn tại với provider khác (ví dụ: LOCAL)
                // -> Có thể link tài khoản hoặc báo lỗi yêu cầu đăng nhập bằng cách cũ
                log.warn("User with email {} already exists with provider {}. Cannot link Google account automatically.", email, existingUser.getProvider());
                throw new BadRequestException("Tài khoản với email này đã tồn tại. Vui lòng đăng nhập bằng phương thức ban đầu.");
            }
            // Cập nhật thông tin nếu cần (tên, avatar)
            boolean updated = false;
            if (fullName != null && !fullName.equals(existingUser.getFullName())) {
                existingUser.setFullName(fullName);
                updated = true;
            }
            if (avatarUrl != null && !avatarUrl.equals(existingUser.getAvatarUrl())) {
                existingUser.setAvatarUrl(avatarUrl);
                updated = true;
            }
            if (providerId != null && !providerId.equals(existingUser.getProviderId())) {
                existingUser.setProviderId(providerId); // Lưu providerId nếu chưa có
                updated = true;
            }
            if (updated) {
                userRepository.save(existingUser);
            }
            return existingUser; // Trả về user đã tồn tại
        } else {
            // Tạo user mới
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFullName(fullName != null ? fullName : "Người dùng Google"); // Tên mặc định
            newUser.setAvatarUrl(avatarUrl);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); // Tạo mật khẩu ngẫu nhiên, không dùng được
            newUser.setActive(true); // Email đã được Google xác thực
            newUser.setProvider(provider); // Đặt provider là GOOGLE
            newUser.setProviderId(providerId); // Lưu Google User ID

            // Gán vai trò mặc định
            Role defaultRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", RoleType.ROLE_CONSUMER.name()));
            newUser.setRoles(Collections.singleton(defaultRole));

            log.info("Creating new user from Google login: {}", email);
            return userRepository.save(newUser);
        }
    }

    // Hàm helper map roles (cần có trong class này hoặc UserDetailsServiceImpl)
    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Set<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
    }




}