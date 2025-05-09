package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.common.util.MoMoUtils; // Import lớp tiện ích
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.dto.response.PaymentUrlResponse;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // Dùng RestTemplate để gọi API MoMo

import java.util.HashMap;
import java.util.Map;

@Service("moMoService") // Đặt tên bean
@RequiredArgsConstructor
@Slf4j
public class MoMoServiceImpl implements PaymentGatewayService {

    @Value("${payment.momo.partnerCode}") private String partnerCode;
    @Value("${payment.momo.accessKey}") private String accessKey;
    @Value("${payment.momo.secretKey}") private String secretKey;
    @Value("${payment.momo.endpoint}") private String momoEndpoint;
    // app.frontend.momoReturnUrl và app.backend.momoIpnUrl sẽ được truyền vào hàm

    private final RestTemplate restTemplate; // Inject RestTemplate (cần tạo Bean cho nó)

    @Override
    public PaymentUrlResponse createMoMoPaymentUrl(Order order, String clientReturnUrl, String backendIpnUrl) {
        String requestId = MoMoUtils.generateRequestId();
        String momoOrderId = MoMoUtils.generateOrderId("AGRITRADE_"); // MoMo có orderId riêng
        String amount = String.valueOf(order.getTotalAmount().longValue()); // MoMo dùng số nguyên
        String orderInfo = "Thanh toan don hang " + order.getOrderCode();
        String requestType = "captureWallet"; // Hoặc "payWithATM", "payWithCC" tùy theo loại bạn muốn tích hợp
        String extraData = ""; // Dữ liệu bổ sung, có thể base64 encode nếu cần

        // Tạo rawHashData theo đúng định dạng MoMo yêu cầu
        String rawHashData = MoMoUtils.buildRawHashData(partnerCode, accessKey, requestId, amount, momoOrderId,
                orderInfo, clientReturnUrl, backendIpnUrl, extraData, requestType);
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
            requestBody.put("ipnUrl", backendIpnUrl);       // MoMo dùng ipnUrl
            requestBody.put("lang", "vi");
            requestBody.put("extraData", extraData);
            requestBody.put("requestType", requestType);
            requestBody.put("signature", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Gọi API MoMo để tạo giao dịch
            Map<String, Object> momoResponse = restTemplate.postForObject(momoEndpoint, entity, Map.class);

            if (momoResponse != null && "0".equals(String.valueOf(momoResponse.get("resultCode")))) {
                String payUrl = (String) momoResponse.get("payUrl");
                log.info("MoMo Payment URL for order {}: {}", order.getOrderCode(), payUrl);
                return new PaymentUrlResponse(payUrl, PaymentMethod.MOMO.name());
            } else {
                String message = momoResponse != null ? (String) momoResponse.get("message") : "Unknown error from MoMo";
                log.error("Error creating MoMo payment URL for order {}: {}", order.getOrderCode(), message);
                throw new RuntimeException("Failed to create MoMo payment: " + message);
            }
        } catch (Exception e) {
            log.error("Exception creating MoMo payment URL for order {}: {}", order.getOrderCode(), e.getMessage(), e);
            throw new RuntimeException("Failed to create MoMo payment due to an exception.", e);
        }
    }

    @Override
    public PaymentUrlResponse createVnPayPaymentUrl(Order order, String ipAddress, String clientReturnUrl) {
        // Để trống hoặc throw UnsupportedOperationException nếu service này chỉ dành cho MoMo
        log.warn("createVnPayPaymentUrl called on MoMoServiceImpl. This is not supported.");
        return null;
    }
}