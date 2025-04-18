package com.yourcompany.agritradels.usermanagement.service.impl;

import com.yourcompany.agritradels.common.exception.ResourceNotFoundException;
import com.yourcompany.agritradels.common.exception.BadRequestException;
import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.notification.service.EmailService;
import com.yourcompany.agritradels.notification.service.NotificationService;
import com.yourcompany.agritradels.usermanagement.domain.Role;
import com.yourcompany.agritradels.usermanagement.domain.User;
// Cập nhật import cho DTOs
import com.yourcompany.agritradels.usermanagement.dto.request.PasswordChangeRequest;
import com.yourcompany.agritradels.usermanagement.dto.request.UserRegistrationRequest;
import com.yourcompany.agritradels.usermanagement.dto.request.UserUpdateRequest;
import com.yourcompany.agritradels.usermanagement.dto.response.UserProfileResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.UserResponse;
import com.yourcompany.agritradels.usermanagement.mapper.BusinessProfileMapper;
import com.yourcompany.agritradels.usermanagement.mapper.FarmerProfileMapper;
import com.yourcompany.agritradels.usermanagement.mapper.UserMapper;
import com.yourcompany.agritradels.usermanagement.repository.BusinessProfileRepository;
import com.yourcompany.agritradels.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritradels.usermanagement.repository.RoleRepository;
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;
import com.yourcompany.agritradels.usermanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private final NotificationService notificationService;
    @Value("${app.frontend.url:http://localhost:4200}") // Lấy URL frontend
    private String frontendUrl;

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
        String verificationUrl = frontendUrl + "/verify-email?token=" + token; // Hoặc /api/auth/verify?token=
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

            String resetUrl = frontendUrl + "/reset-password?token=" + token;
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



}