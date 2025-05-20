package com.yourcompany.agritrade.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider tokenProvider;
  // private final UserDetailsService userDetailsService; // Chính là UserDetailsServiceImpl

  private final TokenBlacklistService tokenBlacklistService;

  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String TOKEN_PREFIX = "Bearer ";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String jwt = getJwtFromRequest(request);

      if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
        // << THÊM KIỂM TRA BLACKLIST >>
        String jti = tokenProvider.getJtiFromToken(jwt);
        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Set status 401
          response
              .getWriter()
              .write(
                  "{\"success\": false, \"message\": \"Token has been invalidated (logged out).\"}"); // Gửi JSON response
          response.setContentType("application/json");
          return; // Dừng filter chain ở đây
        } else {
          // Nếu không bị blacklist (hoặc không lấy được JTI - trường hợp này token có thể không hợp
          // lệ
          String email = tokenProvider.getEmailFromToken(jwt);

          // Load user từ DB (qua UserDetailsService)
          // UserDetails userDetails = userDetailsService.loadUserByUsername(email);

          // Lấy authorities trực tiếp từ token (nhanh hơn nhưng cần đảm bảo token hợp lệ)
          List<String> authoritiesStrings = tokenProvider.getAuthoritiesFromToken(jwt);
          List<SimpleGrantedAuthority> authorities =
              authoritiesStrings.stream()
                  .map(SimpleGrantedAuthority::new)
                  .collect(Collectors.toList());

          // Tạo đối tượng Authentication với email và authorities từ token
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  email, null, authorities); // Principal giờ là email (String)
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

          SecurityContextHolder.getContext().setAuthentication(authentication);
          log.debug(
              "Set Authentication to security context for user '{}', authorities: {}",
              email,
              authoritiesStrings);
        }
      }
    } catch (Exception ex) {
      log.error("Could not set user authentication in security context", ex);
    }

    // Tiếp tục chuỗi filter
    filterChain.doFilter(request, response);
  }

  // Helper method để lấy token từ header
  private String getJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader(HEADER_AUTHORIZATION);
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
      return bearerToken.substring(TOKEN_PREFIX.length());
    }
    return null;
  }
}
