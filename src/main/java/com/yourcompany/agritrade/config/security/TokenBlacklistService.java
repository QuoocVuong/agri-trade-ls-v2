package com.yourcompany.agritrade.config.security; // Hoặc một package service phù hợp

import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

  private final StringRedisTemplate redisTemplate;
  private static final String BLACKLIST_PREFIX = "jwt_blacklist:";

  /**
   * Thêm JTI của token vào blacklist. Token sẽ tự động bị xóa khỏi blacklist khi nó hết hạn.
   *
   * @param jti JWT ID của token.
   * @param expiryDate Thời gian hết hạn của token.
   */
  public void addToBlacklist(String jti, Date expiryDate) {
    if (jti == null || expiryDate == null) {
      log.warn("Attempted to add null JTI or expiry date to blacklist. Skipping.");
      return;
    }
    long now = System.currentTimeMillis();
    long ttlMillis =
        expiryDate.getTime() - now; // Thời gian còn lại của token (tính bằng mili giây)

    if (ttlMillis > 0) {
      try {
        // Key trong Redis sẽ là "jwt_blacklist:jti_value"
        // Value có thể là bất cứ gì, ví dụ "blacklisted" hoặc thời gian blacklist
        redisTemplate
            .opsForValue()
            .set(BLACKLIST_PREFIX + jti, "blacklisted", ttlMillis, TimeUnit.MILLISECONDS);
        log.info("JTI {} added to blacklist with TTL: {} ms", jti, ttlMillis);
      } catch (Exception e) {
        log.error("Error adding JTI {} to Redis blacklist: {}", jti, e.getMessage(), e);
        // Cân nhắc xử lý lỗi này, ví dụ: nếu Redis không hoạt động, logout có thể không hiệu quả
        // 100%
      }
    } else {
      log.warn(
          "Attempted to add JTI {} to blacklist, but its expiry date is in the past. Skipping.",
          jti);
    }
  }

  /**
   * Kiểm tra xem JTI của token có trong blacklist không.
   *
   * @param jti JWT ID của token.
   * @return true nếu token đã bị blacklist, false nếu không.
   */
  public boolean isBlacklisted(String jti) {
    if (jti == null) {
      return false; // Không thể kiểm tra JTI null
    }
    try {
      Boolean isBlacklisted = redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
      return Boolean.TRUE.equals(isBlacklisted);
    } catch (Exception e) {
      log.error("Error checking JTI {} in Redis blacklist: {}", jti, e.getMessage(), e);
      // Trong trường hợp lỗi kết nối Redis, để an toàn, có thể coi là không bị blacklist
      // hoặc ném lỗi để ngăn chặn truy cập nếu chính sách bảo mật yêu cầu.
      // Hiện tại, trả về false để không block người dùng nếu Redis lỗi.
      return false;
    }
  }
}
