package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.util.MoMoUtils;
import com.yourcompany.agritrade.common.util.VnPayUtils;
import com.yourcompany.agritrade.ordering.dto.request.PaymentCallbackRequest;
import com.yourcompany.agritrade.ordering.service.PaymentService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/callback") // Base path cho callback
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

  private final PaymentService paymentService;

  @Value("${payment.vnpay.hashSecret}")
  private String vnpHashSecret;

  @Value("${payment.momo.secretKey}")
  private String momoSecretKey;

  // Endpoint cho VNPay IPN (VNPay thường dùng GET cho IPN)
  @GetMapping("/vnpay/ipn")
  public ResponseEntity<Map<String, String>> handleVnpayIpn(
      @RequestParam Map<String, String> vnpayParams) {
    log.info("Received VNPay IPN: {}", vnpayParams);
    Map<String, String> response = new HashMap<>();
    try {
      // 1. Lấy vnp_SecureHash từ params
      String receivedSecureHash = vnpayParams.get("vnp_SecureHash");
      if (receivedSecureHash == null || receivedSecureHash.isEmpty()) {
        log.error("VNPay IPN Error: Missing vnp_SecureHash");
        response.put("RspCode", "97"); // Mã lỗi của VNPay: Chữ ký không hợp lệ
        response.put("Message", "Invalid Signature");
        return ResponseEntity.ok(response); // Vẫn trả về 200 OK theo yêu cầu VNPay
      }

      // 2. Tạo lại chuỗi hash data từ các tham số khác (loại bỏ vnp_SecureHash và
      // vnp_SecureHashType)
      Map<String, String> fieldsToHash = new HashMap<>(vnpayParams);
      fieldsToHash.remove("vnp_SecureHash");
      fieldsToHash.remove("vnp_SecureHashType"); // Nếu có

      // Sắp xếp và tạo chuỗi hash data
      List<String> fieldNames = new ArrayList<>(fieldsToHash.keySet());
      Collections.sort(fieldNames);
      StringBuilder hashData = new StringBuilder();
      for (String fieldName : fieldNames) {
        String fieldValue = fieldsToHash.get(fieldName);
        if (fieldValue != null && fieldValue.length() > 0) {
          if (hashData.length() > 0) {
            hashData.append('&');
          }
          hashData.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
          hashData.append('=');
          hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
        }
      }

      // 3. Tạo checksum mới và so sánh
      String calculatedSecureHash = VnPayUtils.hmacSHA512(vnpHashSecret, hashData.toString());

      if (calculatedSecureHash.equals(receivedSecureHash)) {
        log.info("VNPay IPN Signature VALIDATED.");
        PaymentCallbackRequest callbackData =
            convertVnpayParamsToDto(vnpayParams); // Chuyển đổi sang DTO
        paymentService.handlePaymentCallback(
            "VNPAY_IPN", callbackData); // Truyền thêm rawParams nếu cần

        // Phản hồi cho VNPay theo tài liệu
        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
      } else {
        log.error(
            "VNPay IPN Error: Invalid Signature. Received: {}, Calculated: {}",
            receivedSecureHash,
            calculatedSecureHash);
        response.put("RspCode", "97");
        response.put("Message", "Invalid Signature");
      }
    } catch (Exception e) {
      log.error("Error processing VNPay IPN: {}", e.getMessage(), e);
      // Trong trường hợp lỗi nghiêm trọng, vẫn cố gắng trả về response mà VNPay mong đợi
      response.put("RspCode", "99"); // Lỗi không xác định
      response.put("Message", "Unknown error");
    }
    return ResponseEntity.ok(response); // Luôn trả về 200 OK với body JSON theo yêu cầu VNPay
  }

  // Helper chuyển đổi params (có thể tách ra VnPayUtils)
  private PaymentCallbackRequest convertVnpayParamsToDto(Map<String, String> params) {
    PaymentCallbackRequest data = new PaymentCallbackRequest();
    data.setOrderCode(params.get("vnp_TxnRef"));
    data.setTransactionCode(params.get("vnp_TransactionNo")); // Mã giao dịch của VNPay
    data.setSuccess("00".equals(params.get("vnp_ResponseCode")));
    try {
      if (params.containsKey("vnp_Amount")) {
        data.setAmount(
            new BigDecimal(params.get("vnp_Amount"))
                .divide(new BigDecimal(100))); // Chia lại cho 100
      }
    } catch (NumberFormatException e) {
      log.error("Error parsing vnp_Amount: {}", params.get("vnp_Amount"), e);
    }
    data.setErrorMessage(params.get("vnp_Message")); // Hoặc các mã lỗi khác
    // Thêm các trường khác nếu cần
    return data;
  }

  // Endpoint cho MoMo IPN (MoMo thường dùng POST)
  @PostMapping("/momo/ipn") // Đường dẫn khớp với ipnUrl bạn cấu hình
  public ResponseEntity<Void> handleMomoIpn(@RequestBody Map<String, Object> momoParams) {
    log.info("Received MoMo IPN: {}", momoParams);
    try {
      // 1. Lấy chữ ký từ MoMo
      String receivedSignature = (String) momoParams.get("signature");
      if (receivedSignature == null || receivedSignature.isEmpty()) {
        log.error("MoMo IPN Error: Missing signature");
        return ResponseEntity.badRequest().build(); // MoMo có thể yêu cầu mã lỗi cụ thể
      }

      // 2. Tạo lại rawHashData từ các tham số khác (THEO ĐÚNG THỨ TỰ VÀ TÊN TRƯỜNG MO MO QUY ĐỊNH
      // CHO IPN)
      // Thứ tự và các trường cho IPN có thể khác với khi tạo request thanh toán.
      // VÍ DỤ (CẦN XEM LẠI TÀI LIỆU MO MO CHO IPN):
      String partnerCode = (String) momoParams.get("partnerCode");
      String accessKey =
          (String) momoParams.get("accessKey"); // MoMo có thể không gửi lại accessKey trong IPN
      String requestId = (String) momoParams.get("requestId");
      String amount = String.valueOf(momoParams.get("amount"));
      String orderId = (String) momoParams.get("orderId");
      String orderInfo = (String) momoParams.get("orderInfo");
      String orderType = (String) momoParams.get("orderType"); // requestType khi gửi đi
      String transId = String.valueOf(momoParams.get("transId"));
      String message = (String) momoParams.get("message");
      String localMessage = (String) momoParams.get("localMessage");
      String responseTime = String.valueOf(momoParams.get("responseTime"));
      String errorCode = String.valueOf(momoParams.get("resultCode"));
      String payType = (String) momoParams.get("payType");
      String extraData = (String) momoParams.get("extraData");

      // Chuỗi này PHẢI GIỐNG HỆT cách MoMo tạo chữ ký cho IPN
      String rawHashData =
          "accessKey="
              + accessKey
              + // Có thể MoMo không dùng accessKey ở đây
              "&amount="
              + amount
              + "&extraData="
              + extraData
              + "&message="
              + message
              + "&orderId="
              + orderId
              + "&orderInfo="
              + orderInfo
              + "&orderType="
              + orderType
              + "&partnerCode="
              + partnerCode
              + "&payType="
              + payType
              + "&requestId="
              + requestId
              + "&responseTime="
              + responseTime
              + "&resultCode="
              + errorCode
              + "&transId="
              + transId;
      // LƯU Ý: Đây chỉ là ví dụ, bạn PHẢI xem tài liệu MoMo để biết chính xác các trường và thứ tự
      // cho IPN signature.

      String calculatedSignature = MoMoUtils.signHmacSHA256(rawHashData, momoSecretKey);

      if (calculatedSignature.equals(receivedSignature)) {
        log.info("MoMo IPN Signature VALIDATED.");
        PaymentCallbackRequest callbackData = convertMomoParamsToDto(momoParams);
        paymentService.handlePaymentCallback("MOMO_IPN", callbackData);
        // MoMo thường yêu cầu response trống với status 204 hoặc 200
        return ResponseEntity.noContent().build();
      } else {
        log.error(
            "MoMo IPN Error: Invalid Signature. Received: {}, Calculated: {}, RawData: {}",
            receivedSignature,
            calculatedSignature,
            rawHashData);
        return ResponseEntity.badRequest().build(); // Hoặc mã lỗi MoMo yêu cầu
      }

    } catch (Exception e) {
      log.error("Error processing MoMo IPN: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // Helper chuyển đổi params (có thể tách ra MoMoUtils)
  private PaymentCallbackRequest convertMomoParamsToDto(Map<String, Object> params) {
    PaymentCallbackRequest data = new PaymentCallbackRequest();
    data.setOrderCode(
        (String)
            params.get("orderId")); // Đây là orderId của MoMo, bạn cần map nó về orderCode của bạn
    // Hoặc bạn đã lưu orderCode của bạn vào extraData khi tạo request
    data.setTransactionCode(String.valueOf(params.get("transId")));
    data.setSuccess(
        Integer.valueOf(0).equals(params.get("resultCode"))); // resultCode = 0 là thành công
    try {
      if (params.get("amount") != null) {
        data.setAmount(new BigDecimal(String.valueOf(params.get("amount"))));
      }
    } catch (NumberFormatException e) {
      log.error("Error parsing MoMo amount: {}", params.get("amount"), e);
    }
    data.setErrorMessage((String) params.get("message"));
    return data;
  }

  // --- Helper methods để chuyển đổi params ---
  private PaymentCallbackRequest convertVnpayParams(Map<String, String> params) {

    PaymentCallbackRequest data = new PaymentCallbackRequest();
    data.setOrderCode(params.get("vnp_TxnRef"));
    data.setTransactionCode(params.get("vnp_TransactionNo"));
    // vnp_ResponseCode == "00" là thành công
    data.setSuccess("00".equals(params.get("vnp_ResponseCode")));
    // ... lấy các trường khác
    return data;
  }

  private PaymentCallbackRequest convertMomoParams(Map<String, Object> params) {

    PaymentCallbackRequest data = new PaymentCallbackRequest();
    data.setOrderCode((String) params.get("orderId")); // Ví dụ
    data.setTransactionCode(String.valueOf(params.get("transId"))); // Ví dụ
    // resultCode == 0 là thành công
    data.setSuccess(Integer.valueOf(0).equals(params.get("resultCode")));
    // ... lấy các trường khác
    return data;
  }
}
