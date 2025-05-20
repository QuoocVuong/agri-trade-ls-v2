package com.yourcompany.agritrade.common.util; // Hoặc package utils phù hợp

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MoMoUtils {

  public static String generateRequestId() {
    return UUID.randomUUID().toString();
  }

  public static String generateOrderId(String prefix) { // MoMo cũng dùng orderId riêng của họ
    return prefix + System.currentTimeMillis();
  }

  public static String signHmacSHA256(String data, String secretKey)
      throws NoSuchAlgorithmException, InvalidKeyException {
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(secretKeySpec);
    byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(rawHmac);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    Formatter formatter = new Formatter(sb);
    for (byte b : bytes) {
      formatter.format("%02x", b);
    }
    formatter.close();
    return sb.toString();
  }

  // Hàm tạo chuỗi rawHashData theo đúng thứ tự các trường MoMo yêu cầu
  // Ví dụ (cần xem tài liệu MoMo để biết chính xác các trường và thứ tự):
  public static String buildRawHashData(
      String partnerCode,
      String accessKey,
      String requestId,
      String amount,
      String orderId,
      String orderInfo,
      String returnUrl,
      String notifyUrl,
      String extraData,
      String requestType) {
    return "accessKey="
        + accessKey
        + "&amount="
        + amount
        + "&extraData="
        + extraData
        + "&ipnUrl="
        + notifyUrl
        + "&orderId="
        + orderId
        + "&orderInfo="
        + orderInfo
        + "&partnerCode="
        + partnerCode
        + "&redirectUrl="
        + returnUrl
        + "&requestId="
        + requestId
        + "&requestType="
        + requestType;
  }
}
