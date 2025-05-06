// OAuth2Controller.java
package com.yourcompany.agritrade.usermanagement.controller;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.GoogleLoginRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AuthResponse; // DTO chứa JWT
import com.yourcompany.agritrade.usermanagement.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth2") // Hoặc /api/auth
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

    private final UserService userService;

    @PostMapping("/google/verify") // Endpoint nhận ID Token từ Frontend
    public ResponseEntity<ApiResponse<AuthResponse>> verifyGoogleToken(@Valid @RequestBody GoogleLoginRequest request) {
        try {
            String jwt = userService.processGoogleLogin(request.getIdToken());
            AuthResponse authResponse = new AuthResponse(jwt); // Tạo response chứa JWT

            // Hoặc nếu muốn thêm thông tin khác (ví dụ: expiry)
            // long expiresIn = tokenProvider.getExpirationMsFromToken(jwt); // Cần thêm hàm này vào JwtTokenProvider
            // AuthResponse authResponse = new AuthResponse(jwt, "Bearer", expiresIn / 1000); // Ví dụ


            return ResponseEntity.ok(ApiResponse.success(authResponse, "Google Sign-In successful"));
        } catch (Exception e) {
            log.error("Google Sign-In failed: {}", e.getMessage());
            // Trả về lỗi cụ thể hơn nếu có thể
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("Google Sign-In failed: " + e.getMessage()));
        }
    }
}