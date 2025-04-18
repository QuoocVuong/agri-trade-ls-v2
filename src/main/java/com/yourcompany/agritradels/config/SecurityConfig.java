package com.yourcompany.agritradels.config;

import com.yourcompany.agritradels.config.security.JwtAuthenticationEntryPoint; // Sẽ tạo
import com.yourcompany.agritradels.config.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Bật @PreAuthorize
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true) // Bật các loại annotation bảo mật
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint unauthorizedHandler; // Xử lý lỗi 401

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cần bean này để inject vào AuthController cho việc login
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
    // *** Bean cấu hình CORS ***
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cho phép origin của Angular dev server
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
        // Cho phép các method HTTP phổ biến
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // Cho phép các header cần thiết (bao gồm cả Authorization cho JWT)
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        // Cho phép gửi cookie và header Authorization
        configuration.setAllowCredentials(true);
        // Thời gian cache kết quả preflight request (giây)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Áp dụng cấu hình CORS này cho tất cả các đường dẫn API
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler)) // Xử lý lỗi 401
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify", // Cho phép verify email
                                "/api/auth/forgot-password", // Cho phép yêu cầu quên mật khẩu
                                "/api/auth/reset-password" // Cho phép reset mật khẩu
                        ).permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll() // Cho phép truy cập Swagger UI
                        .requestMatchers("/api/public/**").permitAll() // Ví dụ API public lấy sản phẩm
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/api/files/download/**").permitAll() // Cho phép xem ảnh public

                        // .requestMatchers("/api/admin/**").hasRole("ADMIN") // Ví dụ bảo vệ API admin
                        // .requestMatchers("/api/farmer/**").hasRole("FARMER") // Ví dụ bảo vệ API farmer
                        .anyRequest().authenticated() // Tất cả các request khác cần xác thực
                );

        // Thêm JWT filter trước filter UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}