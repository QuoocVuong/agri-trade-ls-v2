package com.yourcompany.agritrade.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.security.jwt")
@Getter
@Setter
public class JwtProperties {
  private String secret;
  private long expirationMs;
  private RefreshToken refreshToken;

  @Getter
  @Setter
  public static class RefreshToken {
    private long expirationMs;
  }
}
