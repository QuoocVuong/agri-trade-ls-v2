// src/test/java/com/yourcompany/agritrade/config/TestSecurityConfig.java
package com.yourcompany.agritrade.config;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.yourcompany.agritrade.config.properties.JwtProperties;
import com.yourcompany.agritrade.config.security.JwtAuthenticationEntryPoint;
import com.yourcompany.agritrade.config.security.JwtAuthenticationFilter;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.TokenBlacklistService;
import com.yourcompany.agritrade.config.security.UserDetailsServiceImpl;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@TestConfiguration
@EnableMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true) // BẬT METHOD SECURITY CHO TEST
public class TestSecurityConfig {

  @Bean
  @Primary
  public JwtProperties jwtProperties() {
    JwtProperties jwtProps = mock(JwtProperties.class);
    JwtProperties.RefreshToken mockRefreshTokenProps = new JwtProperties.RefreshToken();
    mockRefreshTokenProps.setExpirationMs(TimeUnit.DAYS.toMillis(7));
    lenient()
        .when(jwtProps.getSecret())
        .thenReturn(
            "c29tZXRoaW5nZWxzZWdldGhpbmdzZWxzZWdldGhpbmdzZWxzZWdldGhpbmdzZWxzZWdldGhpbmdzZWxzZQ==");
    lenient().when(jwtProps.getExpirationMs()).thenReturn(3600000L);
    lenient().when(jwtProps.getRefreshToken()).thenReturn(mockRefreshTokenProps);
    return jwtProps;
  }

  @Bean
  @Primary
  public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
    return mock(JwtTokenProvider.class);
  }

  @Bean
  @Primary
  public TokenBlacklistService tokenBlacklistService() {
    return mock(TokenBlacklistService.class);
  }

  @Bean
  @Primary
  public UserDetailsServiceImpl userDetailsServiceImpl() {
    return mock(UserDetailsServiceImpl.class);
  }

  @Bean
  @Primary
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtTokenProvider jwtTokenProvider, TokenBlacklistService tokenBlacklistService) {
    return new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService);
  }

  @Bean
  @Primary
  public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
    return mock(JwtAuthenticationEntryPoint.class);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      JwtAuthenticationEntryPoint unauthorizedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Cho phép các đường dẫn public cụ thể nếu cần cho các test khác
                    .requestMatchers(
                        "/api/public/**",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/auth/**",
                        "/api/files/download/**",
                        "/api/reviews/product/**",
                        "/api/follows/followers/user/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated() // Yêu cầu xác thực cho các request khác
            )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return mock(PasswordEncoder.class);
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }
}
