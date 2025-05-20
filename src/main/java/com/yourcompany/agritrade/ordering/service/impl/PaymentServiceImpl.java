package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.PaymentCallbackRequest;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.repository.PaymentRepository;
import com.yourcompany.agritrade.ordering.service.PaymentService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  // Inject các service/component cần thiết để xác thực callback
  private final NotificationService notificationService;

  @Override
  @Transactional
  public void handlePaymentCallback(String gateway, PaymentCallbackRequest callbackData) {
    log.info("Received payment callback from [{}]: {}", gateway, callbackData);

    // --- BƯỚC 1: XÁC THỰC CALLBACK ---
    // boolean isValidCallback = validateCallback(gateway, callbackData);
    // if (!isValidCallback) {
    //     log.error("Invalid payment callback received from {}. Data: {}", gateway, callbackData);
    //     // Không nên throw lỗi ra ngoài mà chỉ log lại, vì cổng thanh toán có thể coi là lỗi và
    // gửi lại
    //     // Hoặc throw một exception đặc biệt để controller trả về mã lỗi phù hợp cho cổng thanh
    // toán
    //      throw new BadRequestException("Invalid payment callback signature or data.");
    // }
    log.warn(
        "Callback validation skipped for gateway: {}",
        gateway); // Bỏ qua validation trong ví dụ này

    // --- BƯỚC 2: LẤY THÔNG TIN ---
    String orderCode = callbackData.getOrderCode();
    String transactionCode = callbackData.getTransactionCode(); // Mã giao dịch của cổng thanh toán
    boolean paymentSuccess = callbackData.isSuccess();
    String errorMessage = callbackData.getErrorMessage(); // Lấy lỗi nếu thất bại

    if (orderCode == null) {
      log.error(
          "Order code is missing in payment callback from {}. Data: {}", gateway, callbackData);
      throw new BadRequestException("Missing order code in callback.");
    }

    // --- BƯỚC 3: TÌM ĐƠN HÀNG ---
    // Tìm theo orderCode, không cần fetch hết details
    Order order =
        orderRepository
            .findByOrderCode(orderCode)
            .orElseThrow(
                () -> {
                  log.error(
                      "Order not found for callback with orderCode: {}. Gateway: {}",
                      orderCode,
                      gateway);
                  // Vẫn nên xử lý để trả về thành công cho cổng thanh toán
                  return new BadRequestException("Order not found for callback: " + orderCode);
                });

    // --- BƯỚC 4: TÌM HOẶC TẠO PAYMENT RECORD ---
    // Nên tìm theo transactionCode nếu có, hoặc tạo mới nếu là lần đầu nhận callback cho đơn này
    Payment payment =
        paymentRepository
            .findByTransactionCode(transactionCode) // Ưu tiên tìm theo mã giao dịch cổng TT
            .orElseGet(
                () ->
                    paymentRepository
                        .findByOrderId(
                            order.getId()) // Nếu ko có mã GD, tìm payment PENDING của đơn hàng
                        .stream()
                        .filter(p -> p.getStatus() == PaymentTransactionStatus.PENDING)
                        .findFirst()
                        .orElseGet(
                            () -> { // Nếu ko có payment PENDING -> tạo mới (trường hợp callback đến
                              // trước?)
                              log.warn(
                                  "No PENDING payment found for order {}, creating new one for callback.",
                                  orderCode);
                              Payment newPayment = new Payment();
                              newPayment.setOrder(order);
                              newPayment.setPaymentGateway(gateway.toUpperCase());
                              newPayment.setTransactionCode(transactionCode);
                              newPayment.setAmount(
                                  callbackData.getAmount() != null
                                      ? callbackData.getAmount()
                                      : order.getTotalAmount());
                              newPayment.setStatus(
                                  PaymentTransactionStatus.PENDING); // Bắt đầu là PENDING
                              return newPayment;
                            }));

    // --- BƯỚC 5: CẬP NHẬT TRẠNG THÁI ---
    // Chỉ xử lý nếu giao dịch đang PENDING để tránh xử lý trùng lặp
    if (payment.getStatus() == PaymentTransactionStatus.PENDING) {
      if (paymentSuccess) {
        payment.setStatus(PaymentTransactionStatus.SUCCESS);
        payment.setPaymentTime(LocalDateTime.now()); // Hoặc lấy từ callback
        payment.setGatewayMessage("Payment successful via " + gateway);
        order.setPaymentStatus(PaymentStatus.PAID);
        // Chuyển trạng thái đơn hàng nếu đang PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
          order.setStatus(OrderStatus.CONFIRMED); // Hoặc PROCESSING
        }
        log.info(
            "Payment SUCCESS recorded for order {}, transaction {}", orderCode, transactionCode);
        // TODO: Gửi email/thông báo thanh toán thành công
        // *** Gửi thông báo thanh toán thành công ***
        notificationService.sendPaymentSuccessNotification(order);
      } else {
        payment.setStatus(PaymentTransactionStatus.FAILED);
        payment.setGatewayMessage(
            errorMessage != null ? errorMessage : "Payment failed via " + gateway);
        order.setPaymentStatus(PaymentStatus.FAILED);
        // Giữ nguyên trạng thái PENDING của đơn hàng hoặc chuyển sang CANCELLED tùy logic
        log.error(
            "Payment FAILED recorded for order {}, transaction {}. Reason: {}",
            orderCode,
            transactionCode,
            errorMessage);
        // TODO: Gửi email/thông báo thanh toán thất bại
        // *** Gửi thông báo thanh toán thất bại ***
        notificationService.sendPaymentFailureNotification(order);
      }
      // Lưu lại thay đổi
      paymentRepository.save(payment);
      orderRepository.save(order);
    } else {
      log.warn(
          "Received callback for already processed payment (status: {}) for order {}, transaction {}",
          payment.getStatus(),
          orderCode,
          transactionCode);
    }

    // --- BƯỚC 6: TRẢ VỀ RESPONSE CHO CỔNG THANH TOÁN (QUAN TRỌNG) ---
    // Logic này nằm ở Controller, Service chỉ xử lý nghiệp vụ.
    // Controller cần trả về mã 200 OK hoặc theo yêu cầu của cổng thanh toán.
  }

  private boolean validateCallback(
      String gateway, PaymentCallbackRequest callbackData, Map<String, ?> rawParams) {
    if ("VNPAY".equalsIgnoreCase(gateway)) {
      // Lấy vnp_SecureHash từ rawParams
      // Tạo lại chuỗi hash data từ các tham số khác (trừ vnp_SecureHash) theo đúng thứ tự
      // So sánh hash tự tạo với vnp_SecureHash nhận được
      // return calculatedHash.equals(receivedHash);
      return true; // Tạm thời, PHẢI IMPLEMENT THẬT
    } else if ("MOMO".equalsIgnoreCase(gateway)) {
      // Logic xác thực chữ ký của MoMo
      return true; // Tạm thời, PHẢI IMPLEMENT THẬT
    }
    return false;
  }
}
