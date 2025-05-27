// ordering/scheduler/InvoiceScheduler.java
package com.yourcompany.agritrade.ordering.scheduler;

import com.yourcompany.agritrade.common.model.RoleType; // Thêm import
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository; // Thêm import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Thêm import
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
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository; // Inject UserRepository

    @Value("${app.scheduler.invoice.cron}")
    private String invoiceSchedulerCron;

    @Value("${app.scheduler.invoice.due_soon_days_before:3}")
    private int dueSoonDaysBefore;

    @Value("${app.scheduler.invoice.overdue_admin_notify_after_days:7}") // Thêm cấu hình này
    private int overdueAdminNotifyAfterDays;



    @Scheduled(cron = "${app.scheduler.invoice.cron}")
    @Transactional
    public void checkInvoicesAndSendReminders() { // Đổi tên phương thức cho rõ ràng
        log.info("Scheduled task: Checking invoices and sending reminders - START");
        LocalDate today = LocalDate.now();

        // 1. Xử lý hóa đơn ISSUED đã quá hạn
        List<Invoice> newlyOverdueInvoices = invoiceRepository.findWithDetailsByStatusAndDueDateBefore(InvoiceStatus.ISSUED, today);
        for (Invoice invoice : newlyOverdueInvoices) {
            log.warn("Invoice {} for order {} is overdue (Due date: {}). Marking as OVERDUE.",
                    invoice.getInvoiceNumber(), invoice.getOrder().getOrderCode(), invoice.getDueDate());
            invoice.setStatus(InvoiceStatus.OVERDUE);
            // invoiceRepository.save(invoice); // Sẽ được save cuối transaction nếu có thay đổi

            User buyer = invoice.getOrder().getBuyer();
            if (buyer != null) {
                notificationService.sendOverdueInvoiceReminderToBuyer(invoice);
                emailService.sendOverdueInvoiceReminderEmail(invoice); // Gửi email
            }
        }
        if (!newlyOverdueInvoices.isEmpty()) {
            invoiceRepository.saveAll(newlyOverdueInvoices); // Lưu tất cả thay đổi trạng thái
            log.info("Marked {} invoices as OVERDUE and sent buyer reminders.", newlyOverdueInvoices.size());
        }

        // 2. Gửi thông báo cho Admin về các hóa đơn OVERDUE (có thể có ngưỡng ngày quá hạn)
        LocalDate adminNotifyThresholdDate = today.minusDays(overdueAdminNotifyAfterDays);
        // Lấy tất cả hóa đơn OVERDUE hoặc những hóa đơn mới chuyển sang OVERDUE và quá 1 ngưỡng nhất định
        // Để đơn giản, ta có thể lấy tất cả OVERDUE và service thông báo sẽ quyết định có gửi lại hay không
        // Hoặc, chỉ gửi cho những hóa đơn vừa chuyển trạng thái ở bước trên.
        // Ở đây, ta sẽ gửi cho tất cả các hóa đơn đang là OVERDUE và có dueDate <= adminNotifyThresholdDate
        // để tránh spam admin mỗi ngày cho cùng 1 hóa đơn.
        // Cần một query mới hoặc lọc sau khi lấy.
        // Ví dụ đơn giản: gửi cho những hóa đơn vừa chuyển thành OVERDUE ở trên.
        if (!newlyOverdueInvoices.isEmpty()) {
            List<User> admins = userRepository.findByRoles_Name(RoleType.ROLE_ADMIN);
            if (!admins.isEmpty()) {
                for (Invoice invoice : newlyOverdueInvoices) { // Chỉ thông báo cho những cái mới quá hạn
                    notificationService.sendOverdueInvoiceNotificationToAdmin(invoice);
                    emailService.sendOverdueInvoiceAdminEmail(invoice, admins); // Gửi email cho admin
                }
                log.info("Sent admin notifications for {} newly overdue invoices.", newlyOverdueInvoices.size());
            }
        }


        // 3. Xử lý hóa đơn ISSUED sắp đến hạn
        LocalDate reminderDateThreshold = today.plusDays(dueSoonDaysBefore);
        List<Invoice> dueSoonInvoices = invoiceRepository.findWithDetailsByStatusAndDueDateBetween(InvoiceStatus.ISSUED, today, reminderDateThreshold);
        for (Invoice invoice : dueSoonInvoices) {
            log.info("Invoice {} for order {} is due soon (Due date: {}). Sending reminder.",
                    invoice.getInvoiceNumber(), invoice.getOrder().getOrderCode(), invoice.getDueDate());
            User buyer = invoice.getOrder().getBuyer();
            if (buyer != null) {
                notificationService.sendDueSoonInvoiceReminderToBuyer(invoice);
                emailService.sendDueSoonInvoiceReminderEmail(invoice); // Gửi email
            }
        }
        if (!dueSoonInvoices.isEmpty()) {
            log.info("Sent {} due soon reminders.", dueSoonInvoices.size());
        }

        log.info("Scheduled task: Checking invoices and sending reminders - END");
    }
}