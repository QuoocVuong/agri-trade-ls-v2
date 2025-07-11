package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.common.util.MoMoUtils;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.dto.response.PaymentUrlResponse;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service("moMoService") // Đặt tên bean
@RequiredArgsConstructor
@Slf4j
public class MoMoServiceImpl implements PaymentGatewayService {

  @Value("${payment.momo.partnerCode}")
  private String partnerCode;

  @Value("${payment.momo.accessKey}")
  private String accessKey;

  @Value("${payment.momo.secretKey}")
  private String secretKey;

  @Value("${payment.momo.endpoint}")
  private String momoEndpoint;

  @Value("${payment.momo.refundUrl}") // THÊM URL API HOÀN TIỀN CỦA MOMO
  private String momoRefundUrl;

  @Value("${app.backend.url}")
  private String backendAppUrl;

  private final RestTemplate restTemplate; // Inject RestTemplate

  @Override
  public PaymentUrlResponse createMoMoPaymentUrl(
      Order order, String clientReturnUrl, String backendIpnUrl_unused) {
    String requestId = MoMoUtils.generateRequestId();
    String momoOrderId = MoMoUtils.generateOrderId("AGRITRADE_MOMO_"); // MoMo có orderId riêng
    String amount = String.valueOf(order.getTotalAmount().longValue()); // MoMo dùng số nguyên
    String orderInfo = "Thanh toan don hang " + order.getOrderCode();
    String requestType =
        "captureWallet"; // Hoặc "payWithATM", "payWithCC" tùy theo loại  muốn tích hợp
    String extraData = ""; // Dữ liệu bổ sung, có thể base64 encode nếu cần

    // XÂY DỰNG URL IPN ĐỘNG
    String backendIpnUrl = backendAppUrl + "/api/payments/callback/momo/ipn";

    // Tạo rawHashData theo đúng định dạng MoMo yêu cầu
    String rawHashData =
        MoMoUtils.buildRawHashData(
            partnerCode,
            accessKey,
            requestId,
            amount,
            momoOrderId,
            orderInfo,
            clientReturnUrl,
            backendIpnUrl,
            extraData,
            requestType);
    log.debug("MoMo Raw Hmac Data: {}", rawHashData);

    try {
      String signature = MoMoUtils.signHmacSHA256(rawHashData, secretKey);
      log.debug("MoMo Signature: {}", signature);

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("partnerCode", partnerCode);
      requestBody.put("accessKey", accessKey);
      requestBody.put("requestId", requestId);
      requestBody.put("amount", amount);
      requestBody.put("orderId", momoOrderId);
      requestBody.put("orderInfo", orderInfo);
      requestBody.put("redirectUrl", clientReturnUrl); // MoMo dùng redirectUrl
      requestBody.put("ipnUrl", backendIpnUrl); // MoMo dùng ipnUrl
      requestBody.put("lang", "vi");
      requestBody.put("extraData", extraData);
      requestBody.put("requestType", requestType);
      requestBody.put("signature", signature);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

      // Gọi API MoMo để tạo giao dịch
      Map<String, Object> momoResponse =
          restTemplate.postForObject(momoEndpoint, entity, Map.class);

      if (momoResponse != null && "0".equals(String.valueOf(momoResponse.get("resultCode")))) {
        String payUrl = (String) momoResponse.get("payUrl");
        log.info("MoMo Payment URL for order {}: {}", order.getOrderCode(), payUrl);
        return new PaymentUrlResponse(payUrl, PaymentMethod.MOMO.name());
      } else {
        String message =
            momoResponse != null ? (String) momoResponse.get("message") : "Unknown error from MoMo";
        log.error(
            "Error creating MoMo payment URL for order {}: {}", order.getOrderCode(), message);
        throw new RuntimeException("Failed to create MoMo payment: " + message);
      }
    } catch (Exception e) {
      log.error(
          "Exception creating MoMo payment URL for order {}: {}",
          order.getOrderCode(),
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to create MoMo payment due to an exception.", e);
    }
  }

  @Override
  public PaymentUrlResponse createVnPayPaymentUrl(
      Order order, String ipAddress, String clientReturnUrl) {
    // Để trống hoặc throw UnsupportedOperationException nếu service này chỉ dành cho MoMo
    log.warn("createVnPayPaymentUrl called on MoMoServiceImpl. This is not supported.");
    return null;
  }

  @Override
  public boolean requestRefund(
      String originalTransactionCode, BigDecimal refundAmount, String reason) {
    log.info(
        "Requesting MoMo refund for transaction: {}, amount: {}, reason: {}",
        originalTransactionCode,
        refundAmount,
        reason);

    String requestId = MoMoUtils.generateRequestId();
    String momoOrderId = reason; // MoMo API hoàn tiền thường dùng orderId của giao dịch gốc
    // hoặc một mã yêu cầu hoàn tiền mới.
    // Cần kiểm tra tài liệu MoMo. Giả sử `reason` chứa mã đơn hàng gốc của bạn.
    long amountToRefund = refundAmount.longValue(); // MoMo dùng số nguyên

    // Các tham số cho API hoàn tiền của MoMo có thể khác với API tạo thanh toán.
    // Dưới đây là ví dụ, CẦN THAM KHẢO TÀI LIỆU API HOÀN TIỀN CỦA MOMO.
    // Ví dụ: MoMo có thể yêu cầu `transId` của giao dịch gốc.
    String rawHashData =
        "accessKey="
            + accessKey
            + "&amount="
            + amountToRefund
            + "&description="
            + ("Hoan tien cho " + reason)
            + // Mô tả cho giao dịch hoàn tiền
            "&orderId="
            + momoOrderId
            + // Có thể là orderId của yêu cầu hoàn tiền, hoặc orderId gốc
            "&partnerCode="
            + partnerCode
            + "&requestId="
            + requestId
            + "&transId="
            + originalTransactionCode; // Mã giao dịch gốc của MoMo

    log.debug("MoMo Raw Hmac Data for refund: {}", rawHashData);

    try {
      String signature = MoMoUtils.signHmacSHA256(rawHashData, secretKey);
      log.debug("MoMo Signature for refund: {}", signature);

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("partnerCode", partnerCode);
      requestBody.put("orderId", momoOrderId); // ID của yêu cầu hoàn tiền hoặc ID đơn hàng gốc
      requestBody.put("requestId", requestId);
      requestBody.put("amount", amountToRefund);
      requestBody.put("transId", originalTransactionCode); // Mã giao dịch gốc của MoMo
      requestBody.put("lang", "vi");
      requestBody.put("description", "Hoan tien cho " + reason);
      requestBody.put("signature", signature);
      // Thêm các trường khác theo yêu cầu của MoMo Refund API

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

      // Gọi API hoàn tiền của MoMo
      Map<String, Object> momoRefundResponse =
          restTemplate.postForObject(momoRefundUrl, entity, Map.class); // Sử dụng momoRefundUrl
      log.info("MoMo Refund Response: {}", momoRefundResponse);

      if (momoRefundResponse != null
          && "0".equals(String.valueOf(momoRefundResponse.get("resultCode")))) {
        // resultCode = 0 thường là thành công hoặc yêu cầu đã được chấp nhận
        log.info(
            "MoMo refund request for transaction {} submitted successfully.",
            originalTransactionCode);
        return true;
      } else {
        String message =
            momoRefundResponse != null
                ? (String) momoRefundResponse.get("message")
                : "Unknown MoMo refund error";
        log.error(
            "MoMo refund request for transaction {} failed: {}", originalTransactionCode, message);
        return false;
      }
    } catch (Exception e) {
      log.error(
          "Exception during MoMo refund request for transaction {}: {}",
          originalTransactionCode,
          e.getMessage(),
          e);
      return false;
    }
  }
}
