// src/main/java/com/yourcompany/agritrade/common/util/SecurityUtils.java
package com.yourcompany.agritrade.common.util;

import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component // Đánh dấu là một Spring bean để có thể inject UserRepository
@Slf4j
public class SecurityUtils {

    private static UserRepository staticUserRepository;

    // Constructor injection cho UserRepository
    // Spring sẽ tự động inject UserRepository vào đây khi SecurityUtils được tạo
    public SecurityUtils(UserRepository userRepository) {
        SecurityUtils.staticUserRepository = userRepository;
    }

    /**
     * Lấy thông tin User entity của người dùng đang được xác thực.
     * Phương thức này là static để có thể gọi trực tiếp từ bất kỳ đâu mà không cần inject SecurityUtils.
     *
     * @return User entity của người dùng hiện tại.
     * @throws AccessDeniedException       Nếu không có người dùng nào được xác thực.
     * @throws UsernameNotFoundException   Nếu thông tin xác thực có nhưng không tìm thấy user trong DB.
     */
    public static User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("Attempted to get current user but no user is authenticated.");
            throw new AccessDeniedException("User is not authenticated or authentication details are missing.");
        }

        Object principal = authentication.getPrincipal();
        String userIdentifier;

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            userIdentifier = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            userIdentifier = (String) principal;
        } else if (principal instanceof com.yourcompany.agritrade.usermanagement.domain.User) {
            userIdentifier = ((com.yourcompany.agritrade.usermanagement.domain.User) principal).getEmail();
        }
        else {
            log.error("Unexpected principal type: {}", principal.getClass().getName());
            throw new AccessDeniedException("Cannot identify user from authentication principal.");
        }

        if (staticUserRepository == null) {
            log.error("UserRepository has not been injected into SecurityUtils. This typically happens if SecurityUtils is not managed by Spring or an issue with component scanning.");
            throw new IllegalStateException("UserRepository not available in SecurityUtils. Ensure SecurityUtils is a Spring managed bean and UserRepository is correctly injected.");
        }

        return staticUserRepository.findByEmail(userIdentifier)
                .orElseThrow(() -> {
                    log.error("Authenticated user not found in database with identifier: {}", userIdentifier);
                    return new UsernameNotFoundException("Authenticated user not found: " + userIdentifier);
                });
    }

    /**
     * Lấy đối tượng Authentication hiện tại từ SecurityContextHolder.
     *
     * @return Đối tượng Authentication, hoặc null nếu không có.
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò được chỉ định hay không.
     *
     * @param roleName Tên vai trò cần kiểm tra (ví dụ: "ROLE_ADMIN").
     * @return true nếu người dùng có vai trò đó, false nếu không.
     */
    public static boolean hasRole(String roleName) {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(roleName));
    }

    /**
     * Kiểm tra xem người dùng hiện tại có quyền hạn được chỉ định hay không.
     *
     * @param authorityName Tên quyền hạn cần kiểm tra (ví dụ: "USER_READ_ALL").
     * @return true nếu người dùng có quyền hạn đó, false nếu không.
     */
    public static boolean hasAuthority(String authorityName) {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authorityName));
    }
}