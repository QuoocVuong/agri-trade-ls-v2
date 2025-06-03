package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.common.util.VnPayUtils;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.dto.response.PaymentUrlResponse;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// VnPayServiceImpl.java
@Service("vnPayService")
@RequiredArgsConstructor
@Slf4j
public class VnPayServiceImpl implements PaymentGatewayService {

  @Value("${payment.vnpay.version}")
  private String vnpVersion;

  @Value("${payment.vnpay.tmnCode}")
  private String vnpTmnCode;

  @Value("${payment.vnpay.hashSecret}")
  private String vnpHashSecret;

  @Value("${payment.vnpay.apiUrl}") // URL API của VNPay, thường dùng cho truy vấn, hoàn tiền
  private String vnpApiUrl;

  @Value("${payment.vnpay.url}")
  private String vnpPayUrl;

  @Value("${app.backend.vnpayIpnUrl}")
  private String vnpIpnUrl; // URL Backend nhận IPN

  private final RestTemplate restTemplate;

  @Override
  public PaymentUrlResponse createVnPayPaymentUrl(
      Order order, String ipAddress, String clientReturnUrl) {
    // Số tiền cần nhân 100 theo quy định của VNPay
    long amount = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();

    Map<String, String> vnp_Params = new HashMap<>();
    vnp_Params.put("vnp_Version", vnpVersion);
    vnp_Params.put("vnp_Command", "pay");
    vnp_Params.put("vnp_TmnCode", vnpTmnCode);
    vnp_Params.put("vnp_Amount", String.valueOf(amount));
    vnp_Params.put("vnp_CurrCode", "VND");
    // vnp_Params.put("vnp_BankCode", ""); // Bỏ trống để hiển thị cổng chọn ngân hàng, hoặc truyền
    // mã cụ thể
    vnp_Params.put("vnp_TxnRef", order.getOrderCode()); // Mã đơn hàng của bạn
    vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
    vnp_Params.put("vnp_OrderType", "other"); // Loại hàng hóa, xem tài liệu VNPay
    vnp_Params.put("vnp_Locale", "vn");
    vnp_Params.put("vnp_ReturnUrl", clientReturnUrl); // URL Frontend trả về
    vnp_Params.put("vnp_IpAddr", ipAddress);

    Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
    String vnp_CreateDate = formatter.format(cld.getTime());
    vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

    cld.add(Calendar.MINUTE, 15); // Thời gian hết hạn thanh toán (ví dụ 15 phút)
    String vnp_ExpireDate = formatter.format(cld.getTime());
    vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

    // Build data query string
    List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
    Collections.sort(fieldNames);
    StringBuilder hashData = new StringBuilder();
    StringBuilder query = new StringBuilder();
    Iterator<String> itr = fieldNames.iterator();
    while (itr.hasNext()) {
      String fieldName = itr.next();
      String fieldValue = vnp_Params.get(fieldName);
      if ((fieldValue != null) && (fieldValue.length() > 0)) {
        // Build hash data
        hashData.append(fieldName);
        hashData.append('=');
        hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
        // Build query
        query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
        query.append('=');
        query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
        if (itr.hasNext()) {
          query.append('&');
          hashData.append('&');
        }
      }
    }
    String queryUrl = query.toString();
    String vnp_SecureHash = VnPayUtils.hmacSHA512(vnpHashSecret, hashData.toString());
    queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
    String paymentUrl = vnpPayUrl + "?" + queryUrl;

    log.info("VNPay Payment URL for order {}: {}", order.getOrderCode(), paymentUrl);
    return new PaymentUrlResponse(paymentUrl, PaymentMethod.VNPAY.name());
  }

  // Implement createMoMoPaymentUrl nếu cần
  @Override
  public PaymentUrlResponse createMoMoPaymentUrl(Order order, String returnUrl, String notifyUrl) {
    // TODO: Implement MoMo payment URL creation logic
    log.warn("MoMo payment URL creation is not implemented yet.");
    return null;
  }
  @Override
  public boolean requestRefund(String originalTransactionCode, BigDecimal refundAmount, String reason) {
    log.info("Requesting VNPay refund for transaction: {}, amount: {}, reason: {}",
            originalTransactionCode, refundAmount, reason);

    // VNPay yêu cầu số tiền hoàn phải là số nguyên (nhân 100)
    long amountToRefund = refundAmount.multiply(new BigDecimal(100)).longValue();

    Map<String, String> vnp_Params = new HashMap<>();
    vnp_Params.put("vnp_RequestId", UUID.randomUUID().toString()); // Mã yêu cầu duy nhất
    vnp_Params.put("vnp_Version", vnpVersion); // Hoặc phiên bản API hoàn tiền nếu khác
    vnp_Params.put("vnp_Command", "refund"); // Hoặc "partialrefund"
    vnp_Params.put("vnp_TmnCode", vnpTmnCode);
    vnp_Params.put("vnp_TransactionType", "02"); // 02: Hoàn toàn phần, 03: Hoàn toàn bộ (kiểm tra lại tài liệu)
    // Nếu refundAmount bằng số tiền gốc thì có thể là "03"
    vnp_Params.put("vnp_TxnRef", originalTransactionCode); // Mã giao dịch gốc của VNPay (vnp_TransactionNo)
    // HOẶC mã đơn hàng của bạn (vnp_TxnRef) nếu API cho phép
    // CẦN KIỂM TRA KỸ TÀI LIỆU VNPay
    vnp_Params.put("vnp_Amount", String.valueOf(amountToRefund));
    vnp_Params.put("vnp_OrderInfo", "Hoan tien don hang " + reason); // Lý do, có thể là mã đơn hàng
    vnp_Params.put("vnp_TransactionNo", "0"); // Bắt buộc, truyền 0 nếu không có mã giao dịch hoàn tiền trước đó

    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
    vnp_Params.put("vnp_TransactionDate", formatter.format(new Date())); // Thời gian thực hiện giao dịch hoàn tiền
    vnp_Params.put("vnp_CreateBy", "AgriTradeSystem"); // Người tạo yêu cầu
    vnp_Params.put("vnp_CreateDate", formatter.format(new Date()));
    vnp_Params.put("vnp_IpAddr", "127.0.0.1"); // IP server của bạn
    // vnp_Params.put("vnp_SecureHash", ""); // Sẽ được tính toán

    // Sắp xếp và tạo chuỗi hash data
    List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
    Collections.sort(fieldNames);
    StringBuilder hashData = new StringBuilder();
    for (String fieldName : fieldNames) {
      String fieldValue = vnp_Params.get(fieldName);
      if (fieldValue != null && !fieldValue.isEmpty()) {
        if (hashData.length() > 0) {
          hashData.append('&');
        }
        hashData.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
        hashData.append('=');
        hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
      }
    }
    String vnp_SecureHash = VnPayUtils.hmacSHA512(vnpHashSecret, hashData.toString());
    vnp_Params.put("vnp_SecureHash", vnp_SecureHash);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, String>> entity = new HttpEntity<>(vnp_Params, headers);

    try {
      log.debug("Sending VNPay Refund Request: {}", vnp_Params);
      // VNPay Refund API thường là POST và trả về JSON
      ResponseEntity<Map> responseEntity = restTemplate.exchange(vnpApiUrl, HttpMethod.POST, entity, Map.class);
      Map<String, Object> responseBody = responseEntity.getBody();
      log.info("VNPay Refund Response: {}", responseBody);

      if (responseBody != null && "00".equals(responseBody.get("vnp_ResponseCode"))) {
        // "00" thường là thành công hoặc yêu cầu đã được chấp nhận xử lý
        log.info("VNPay refund request for transaction {} submitted successfully.", originalTransactionCode);
        return true;
      } else {
        String message = responseBody != null ? (String) responseBody.get("vnp_Message") : "Unknown VNPay refund error";
        log.error("VNPay refund request for transaction {} failed: {}", originalTransactionCode, message);
        return false;
      }
    } catch (Exception e) {
      log.error("Exception during VNPay refund request for transaction {}: {}", originalTransactionCode, e.getMessage(), e);
      return false;
    }
  }

}
