// src/main/java/com/yourcompany/agritrade/ordering/scheduler/InvoiceScheduler.java
package com.yourcompany.agritrade.ordering.scheduler;

import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.notification.service.NotificationService; // Giả sử bạn có service này
// import com.yourcompany.agritrade.notification.service.EmailService; // Và EmailService
import com.yourcompany.agritrade.usermanagement.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceScheduler {

    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService; // Để gửi thông báo trong ứng dụng
    private final EmailService emailService; // Để gửi email nhắc nhở

    // Ví dụ: Chạy vào 1 giờ sáng mỗi ngày
    // Cấu hình cron: Giây Phút Giờ Ngày Tháng Ngày TrongTuần
    // "0 0 1 * * ?" -> 1:00 AM hàng ngày
    // Để test, bạn có thể dùng "0 * * * * ?" -> Chạy mỗi phút
    @Scheduled(cron = "0 0 1 * * ?") // Chạy vào 1:00 AM hàng ngày
    @Transactional // Quan trọng: để các thay đổi DB được commit
    public void checkOverdueInvoicesAndSendReminders() {
        log.info("Scheduled task: Checking for overdue invoices and sending reminders - START");
        LocalDate today = LocalDate.now();

        // 1. Tìm các hóa đơn ISSUED đã quá hạn
        // Hóa đơn ISSUED và dueDate < today
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.ISSUED, today);
        for (Invoice invoice : overdueInvoices) {
            log.warn("Invoice {} for order {} is overdue (Due date: {}). Marking as OVERDUE.",
                    invoice.getInvoiceNumber(), invoice.getOrder().getOrderCode(), invoice.getDueDate());
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoiceRepository.save(invoice); // Lưu lại trạng thái mới

            // Gửi thông báo/email cho khách hàng (buyer của order)
            User buyer = invoice.getOrder().getBuyer();
            if (buyer != null) {
                // TODO: Implement sendOverdueInvoiceReminderToBuyer in NotificationService/EmailService
                // notificationService.sendOverdueInvoiceReminderToBuyer(invoice);
                // emailService.sendOverdueInvoiceReminderEmail(invoice);
                log.info("Sent overdue reminder for invoice {} to buyer {}", invoice.getInvoiceNumber(), buyer.getEmail());
            }

            // (Tùy chọn) Gửi thông báo cho admin/kế toán
            // TODO: Implement sendOverdueInvoiceNotificationToAdmin in NotificationService/EmailService
            // notificationService.sendOverdueInvoiceNotificationToAdmin(invoice);
        }
        log.info("Marked {} invoices as OVERDUE.", overdueInvoices.size());


        // 2. (Tùy chọn) Tìm các hóa đơn ISSUED sắp đến hạn để gửi nhắc nhở trước
        // Ví dụ: Nhắc trước 3 ngày
        LocalDate reminderDateThreshold = today.plusDays(3); // Hóa đơn có dueDate <= today + 3 days
        List<Invoice> dueSoonInvoices = invoiceRepository.findByStatusAndDueDateBetween(InvoiceStatus.ISSUED, today, reminderDateThreshold);
        for (Invoice invoice : dueSoonInvoices) {
            // Đảm bảo không gửi lại nếu đã là OVERDUE (mặc dù query đã lọc status=ISSUED)
            if (invoice.getStatus() == InvoiceStatus.ISSUED) {
                log.info("Invoice {} for order {} is due soon (Due date: {}). Sending reminder.",
                        invoice.getInvoiceNumber(), invoice.getOrder().getOrderCode(), invoice.getDueDate());
                User buyer = invoice.getOrder().getBuyer();
                if (buyer != null) {
                    // TODO: Implement sendDueSoonInvoiceReminderToBuyer in NotificationService/EmailService
                    // notificationService.sendDueSoonInvoiceReminderToBuyer(invoice);
                    // emailService.sendDueSoonInvoiceReminderEmail(invoice);
                    log.info("Sent due soon reminder for invoice {} to buyer {}", invoice.getInvoiceNumber(), buyer.getEmail());
                }
            }
        }
        log.info("Sent {} due soon reminders.", dueSoonInvoices.size());

        log.info("Scheduled task: Checking for overdue invoices and sending reminders - END");
    }
}