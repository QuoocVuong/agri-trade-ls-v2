package com.yourcompany.agritrade.config.security;

import com.yourcompany.agritrade.config.properties.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException; // Quan trọng: dùng từ io.jsonwebtoken.security
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey; // Quan trọng: dùng từ javax.crypto
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j // Lombok logger
public class JwtTokenProvider {

    private final SecretKey key; // Sử dụng SecretKey thay vì String
    private final long jwtExpirationMs;
    private static final String AUTHORITIES_KEY = "roles"; // Key để lưu roles trong claims

    public JwtTokenProvider(JwtProperties jwtProperties) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes); // Tạo SecretKey an toàn
        this.jwtExpirationMs = jwtProperties.getExpirationMs();
    }

    // Tạo JWT từ thông tin Authentication
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        // Lấy danh sách roles từ authorities
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()); // Thu thập thành List

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .claim(AUTHORITIES_KEY, authorities) // Đưa List authorities vào claim
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    // Lấy email từ JWT
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    // Lấy authorities từ JWT (Hàm mới - hữu ích cho JwtAuthenticationFilter)
    @SuppressWarnings("unchecked") // Bỏ warning unchecked cast
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // Lấy claim "authorities" dưới dạng List
        return (List<String>) claims.get(AUTHORITIES_KEY, List.class);
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
}