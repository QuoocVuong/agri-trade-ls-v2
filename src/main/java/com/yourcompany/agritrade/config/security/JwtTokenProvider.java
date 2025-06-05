package com.yourcompany.agritrade.config.security;

import com.yourcompany.agritrade.config.properties.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Slf4j // Lombok logger
public class JwtTokenProvider {

  private final SecretKey key; // Sử dụng SecretKey
  private final long jwtExpirationMs;
  private static final String AUTHORITIES_KEY = "roles"; // Key để lưu roles trong claims

  private final long refreshTokenExpirationMs;

  public JwtTokenProvider(JwtProperties jwtProperties) {
    byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
    this.key = Keys.hmacShaKeyFor(keyBytes); // Tạo SecretKey an toàn
    this.jwtExpirationMs = jwtProperties.getExpirationMs();
    this.refreshTokenExpirationMs = jwtProperties.getRefreshToken().getExpirationMs();
  }

  // Tạo JWT từ thông tin Authentication
  public String generateAccessToken(Authentication authentication) {
    String username = getUsernameFromAuthentication(authentication);
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

    // Lấy danh sách roles từ authorities
    List<String> authorities =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()); // Thu thập thành List

    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(username)
        .claim(AUTHORITIES_KEY, authorities) // Đưa List authorities vào claim
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(key, SignatureAlgorithm.HS512)
        .compact();
  }

  public String generateRefreshToken(Authentication authentication) {
    String username = getUsernameFromAuthentication(authentication); // Dùng lại helper
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

    return Jwts.builder()
        .setSubject(username) // Subject của refresh token có thể chỉ là username
        // Không cần claim roles cho refresh token, nó chỉ dùng để lấy access token mới
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(key, SignatureAlgorithm.HS512) // Dùng cùng secret key
        .compact();
  }

  // Lấy email từ JWT
  public String getEmailFromToken(String token) {
    Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

    return claims.getSubject();
  }

  @SuppressWarnings("unchecked")
  public List<String> getAuthoritiesFromToken(String token) {
    Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    // Lấy claim "authorities" dưới dạng List
    return (List<String>) claims.get(AUTHORITIES_KEY, List.class);
  }

  public String getJtiFromToken(String token) {
    try {
      Claims claims =
          Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
      return claims.getId();
    } catch (JwtException e) {
      log.warn("Could not get JTI from token: {}", e.getMessage());
      return null;
    }
  }

  public Date getExpiryDateFromToken(String token) {
    try {
      Claims claims =
          Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
      return claims.getExpiration();
    } catch (JwtException e) {
      log.warn("Could not get Expiry Date from token: {}", e.getMessage());
      return null;
    }
  }



  // Xác thực JWT
  public boolean validateToken(String authToken) {
    try {
      Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
      return true;
    } catch (SignatureException ex) {
      log.error("Invalid JWT signature: {}", ex.getMessage());
    } catch (MalformedJwtException ex) {
      log.error("Invalid JWT token: {}", ex.getMessage());
    } catch (ExpiredJwtException ex) {
      log.error("Expired JWT token: {}", ex.getMessage());
    } catch (UnsupportedJwtException ex) {
      log.error("Unsupported JWT token: {}", ex.getMessage());
    } catch (IllegalArgumentException ex) {
      log.error("JWT claims string is empty: {}", ex.getMessage());
    }
    return false;
  }

  private String getUsernameFromAuthentication(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserDetails) {
      return ((UserDetails) principal).getUsername();
    } else if (principal instanceof String) {
      return (String) principal;
    }
    log.error(
        "Cannot determine username from principal type {}",
        principal != null ? principal.getClass() : "null");
    throw new IllegalArgumentException("Cannot determine username from principal");
  }
}
