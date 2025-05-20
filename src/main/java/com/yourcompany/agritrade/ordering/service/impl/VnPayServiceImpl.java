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
import org.springframework.stereotype.Service;

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

  @Value("${payment.vnpay.url}")
  private String vnpPayUrl;

  @Value("${app.backend.vnpayIpnUrl}")
  private String vnpIpnUrl; // URL Backend nhận IPN

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
  // ...
}
