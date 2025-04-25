package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.ordering.dto.request.PaymentCallbackRequest; // Import DTO callback
import com.yourcompany.agritrade.ordering.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map; // Import Map nếu nhận raw data

@RestController
@RequestMapping("/api/payments/callback") // Base path cho callback
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    // Ví dụ endpoint cho VNPay IPN (thường là GET)
    @GetMapping("/vnpay")
    public ResponseEntity<String> handleVnpayCallback(@RequestParam Map<String, String> vnpayParams) {
        log.info("Received VNPay IPN callback: {}", vnpayParams);
        try {
            // TODO: Chuyển đổi vnpayParams sang PaymentCallbackRequest
            PaymentCallbackRequest callbackData = convertVnpayParams(vnpayParams);
            paymentService.handlePaymentCallback("VNPAY", callbackData);

            // TODO: Trả về response theo yêu cầu của VNPay để xác nhận đã nhận IPN
            // Ví dụ: return ResponseEntity.ok("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
            return ResponseEntity.ok("OK"); // Trả về OK 200 nếu xử lý thành công cơ bản

        } catch (Exception e) {
            log.error("Error processing VNPay callback: {}", e.getMessage(), e);
            // Vẫn nên trả về mã thành công cho VNPay để tránh họ gửi lại, nhưng log lỗi nghiêm trọng
            // TODO: Trả về response lỗi theo yêu cầu của VNPay nếu có
            return ResponseEntity.ok("OK BUT FAILED INTERNALLY"); // Hoặc mã lỗi phù hợp
            // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing callback");
        }
    }

    // Ví dụ endpoint cho MoMo IPN (thường là POST)
    @PostMapping("/momo")
    public ResponseEntity<Void> handleMomoCallback(@RequestBody Map<String, Object> momoParams) {
        log.info("Received MoMo IPN callback: {}", momoParams);
        try {
            // TODO: Chuyển đổi momoParams sang PaymentCallbackRequest
            PaymentCallbackRequest callbackData = convertMomoParams(momoParams);
            paymentService.handlePaymentCallback("MOMO", callbackData);

            // MoMo thường không yêu cầu body response, chỉ cần mã 204 No Content hoặc 200 OK
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error processing MoMo callback: {}", e.getMessage(), e);
            // Trả về lỗi nhưng MoMo có thể sẽ thử gửi lại
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    // --- Helper methods để chuyển đổi params ---
    private PaymentCallbackRequest convertVnpayParams(Map<String, String> params) {
        // TODO: Implement logic chuyển đổi dựa trên tài liệu VNPay IPN
        PaymentCallbackRequest data = new PaymentCallbackRequest();
        data.setOrderCode(params.get("vnp_TxnRef"));
        data.setTransactionCode(params.get("vnp_TransactionNo"));
        // vnp_ResponseCode == "00" là thành công
        data.setSuccess("00".equals(params.get("vnp_ResponseCode")));
        // ... lấy các trường khác
        return data;
    }

    private PaymentCallbackRequest convertMomoParams(Map<String, Object> params) {
        // TODO: Implement logic chuyển đổi dựa trên tài liệu MoMo IPN
        PaymentCallbackRequest data = new PaymentCallbackRequest();
        data.setOrderCode((String) params.get("orderId")); // Ví dụ
        data.setTransactionCode(String.valueOf(params.get("transId"))); // Ví dụ
        // resultCode == 0 là thành công
        data.setSuccess(Integer.valueOf(0).equals(params.get("resultCode")));
        // ... lấy các trường khác
        return data;
    }

}