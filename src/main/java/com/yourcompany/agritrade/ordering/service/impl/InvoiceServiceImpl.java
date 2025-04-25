package com.yourcompany.agritrade.ordering.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderItem;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository; // Inject để lấy chi tiết Order
import com.yourcompany.agritrade.ordering.service.InvoiceService;
// Import thư viện tạo PDF (ví dụ iText/OpenPDF)
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository; // Inject để lấy chi tiết Order khi tạo PDF

    @Override
    @Transactional
    public Invoice getOrCreateInvoiceForOrder(Order order) {
        return invoiceRepository.findByOrderId(order.getId())
                .orElseGet(() -> createNewInvoice(order));
    }

    private Invoice createNewInvoice(Order order) {
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceNumber(generateInvoiceNumber(order.getOrderCode()));
        invoice.setIssueDate(LocalDate.now());
        invoice.setTotalAmount(order.getTotalAmount());
        // Logic set dueDate, status tùy theo loại đơn/phương thức thanh toán
        invoice.setStatus(InvoiceStatus.ISSUED); // Ví dụ mặc định là đã phát hành
        log.info("Creating new invoice {} for order {}", invoice.getInvoiceNumber(), order.getId());
        return invoiceRepository.save(invoice);
    }

    private String generateInvoiceNumber(String orderCode) {
        // Ví dụ: INV-LS240421-1234
        return "INV-" + orderCode; // Hoặc logic sinh số phức tạp hơn
    }

    @Override
    @Transactional(readOnly = true) // Chỉ đọc dữ liệu để tạo PDF
    public ByteArrayInputStream generateInvoicePdf(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        // Lấy chi tiết Order để hiển thị thông tin đầy đủ
        Order order = orderRepository.findByIdWithDetails(invoice.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", invoice.getOrder().getId()));


        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4); // Tạo document iText/OpenPDF

//        try {
//            PdfWriter.getInstance(document, out);
//            document.open();
//
//            // --- Thêm nội dung vào PDF ---
//            // (Đây là ví dụ rất cơ bản, bạn cần dùng font hỗ trợ TV và thiết kế layout đẹp hơn)
//            document.add(new Paragraph("HOA DON BAN HANG"));
//            document.add(new Paragraph("So hoa don: " + invoice.getInvoiceNumber()));
//            document.add(new Paragraph("Ngay xuat: " + invoice.getIssueDate().format(DateTimeFormatter.ISO_DATE)));
//            document.add(new Paragraph("Ma don hang: " + order.getOrderCode()));
//            document.add(new Paragraph("---------------------------------"));
//            document.add(new Paragraph("Nguoi ban: " + order.getFarmer().getFullName()));
//            // Thêm thông tin người bán (tên trang trại, địa chỉ...) nếu cần
//            document.add(new Paragraph("Nguoi mua: " + order.getBuyer().getFullName()));
//            // Thêm thông tin người mua (tên công ty, MST - nếu là B2B)
//            document.add(new Paragraph("Dia chi giao hang: " + order.getShippingAddressDetail() + ", " + order.getShippingWardCode() + ", " + order.getShippingDistrictCode() + ", " + order.getShippingProvinceCode()));
//            document.add(new Paragraph("---------------------------------"));
//            document.add(new Paragraph("Chi tiet san pham:"));
//            order.getOrderItems().forEach(item -> {
//                try {
//                    document.add(new Paragraph(String.format("- %s (x%d %s): %s",
//                            item.getProductName(), item.getQuantity(), item.getUnit(), item.getTotalPrice())));
//                } catch (DocumentException e) { /* Handle */ }
//            });
//            document.add(new Paragraph("---------------------------------"));
//            document.add(new Paragraph("Tong tien hang: " + order.getSubTotal()));
//            document.add(new Paragraph("Phi van chuyen: " + order.getShippingFee()));
//            document.add(new Paragraph("Giam gia: " + order.getDiscountAmount()));
//            document.add(new Paragraph("TONG CONG: " + order.getTotalAmount()));
//            document.add(new Paragraph("---------------------------------"));
//            document.add(new Paragraph("Trang thai thanh toan: " + order.getPaymentStatus()));
//
//            document.close();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // *** Cần đăng ký và sử dụng Font tiếng Việt ở đây ***
             Font fontTitle = FontFactory.getFont("../fonts/font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 18, Font.BOLD);
             Font fontNormal = FontFactory.getFont("../fonts/font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10, Font.NORMAL);
//            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18); // Font mặc định, không có TV
//            Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // --- Header ---
            Paragraph title = new Paragraph("HÓA ĐƠN BÁN HÀNG", fontTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("Số: " + invoice.getInvoiceNumber(), fontNormal));
            document.add(new Paragraph("Ngày xuất: " + invoice.getIssueDate().format(DateTimeFormatter.ISO_DATE), fontNormal));
            document.add(Chunk.NEWLINE); // Dòng trống

            // --- Thông tin Người bán / Người mua (Dùng Bảng) ---
            PdfPTable infoTable = new PdfPTable(2); // 2 cột
            infoTable.setWidthPercentage(100);
            infoTable.addCell(createCell("Người bán:", fontNormal, Element.ALIGN_LEFT, false));
            infoTable.addCell(createCell("Người mua:", fontNormal, Element.ALIGN_LEFT, false));
            infoTable.addCell(createCell(order.getFarmer().getFullName(), fontNormal, Element.ALIGN_LEFT, false)); // Cần font TV
            infoTable.addCell(createCell(order.getBuyer().getFullName(), fontNormal, Element.ALIGN_LEFT, false)); // Cần font TV
            // Thêm địa chỉ, MST...
            document.add(infoTable);
            document.add(Chunk.NEWLINE);

            // --- Bảng Chi tiết Sản phẩm ---
            PdfPTable itemTable = new PdfPTable(6); // STT, Tên SP, ĐVT, SL, Đơn giá, Thành tiền
            itemTable.setWidthPercentage(100);
            itemTable.setWidths(new float[]{1, 5, 2, 2, 3, 3}); // Tỷ lệ độ rộng cột
            // Header bảng
            addTableHeader(itemTable, "STT", fontNormal);
            addTableHeader(itemTable, "Tên sản phẩm", fontNormal);
            addTableHeader(itemTable, "ĐVT", fontNormal);
            addTableHeader(itemTable, "Số lượng", fontNormal);
            addTableHeader(itemTable, "Đơn giá", fontNormal);
            addTableHeader(itemTable, "Thành tiền", fontNormal);
            // Dữ liệu item
            int stt = 1;
            for (OrderItem item : order.getOrderItems()) {
                itemTable.addCell(createCell(String.valueOf(stt++), fontNormal, Element.ALIGN_CENTER, true));
                itemTable.addCell(createCell(item.getProductName(), fontNormal, Element.ALIGN_LEFT, true));
                itemTable.addCell(createCell(item.getUnit(), fontNormal, Element.ALIGN_CENTER, true));
                itemTable.addCell(createCell(String.valueOf(item.getQuantity()), fontNormal, Element.ALIGN_RIGHT, true));
                itemTable.addCell(createCell(item.getPricePerUnit().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));
                itemTable.addCell(createCell(item.getTotalPrice().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

            }
            document.add(itemTable);
            document.add(Chunk.NEWLINE);

            // --- Tổng kết hóa đơn ---
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(50);
            summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summaryTable.setWidths(new float[]{3, 3});

            summaryTable.addCell(createCell("Tạm tính:", fontNormal, Element.ALIGN_LEFT, true));
            summaryTable.addCell(createCell(order.getSubTotal().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

            summaryTable.addCell(createCell("Phí vận chuyển:", fontNormal, Element.ALIGN_LEFT, true));
            summaryTable.addCell(createCell(order.getShippingFee().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

            summaryTable.addCell(createCell("Giảm giá:", fontNormal, Element.ALIGN_LEFT, true));
            summaryTable.addCell(createCell(order.getDiscountAmount().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

            summaryTable.addCell(createCell("TỔNG CỘNG:", fontNormal, Element.ALIGN_LEFT, true));
            summaryTable.addCell(createCell(order.getTotalAmount().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

            document.add(summaryTable);
            document.add(Chunk.NEWLINE);

// --- Tổng tiền bằng chữ ---
            String totalAmountText = convertNumberToVietnameseText(order.getTotalAmount().longValue()) + " đồng";
            Paragraph totalAmountTextPara = new Paragraph("Số tiền bằng chữ: " + totalAmountText, fontNormal);
            document.add(totalAmountTextPara);


            document.close();
            writer.close(); // Đóng writer
            log.info("Generated PDF for invoice {}", invoiceId);

        } catch (DocumentException e) {
            log.error("Error generating PDF for invoice {}: {}", invoiceId, e.getMessage());
            throw new RuntimeException("Error generating invoice PDF", e); // Ném lỗi để controller xử lý
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    // Helper tạo cell cho bảng (ví dụ)
    private PdfPCell createCell(String content, Font font, int alignment, boolean border) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        if (!border) {
            cell.setBorder(Rectangle.NO_BORDER);
        }
        return cell;
    }
    // Helper thêm header cho bảng
    private void addTableHeader(PdfPTable table, String headerTitle, Font font) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(Color.LIGHT_GRAY);
        header.setBorderWidth(1);
        header.setPhrase(new Phrase(headerTitle, font));
        header.setHorizontalAlignment(Element.ALIGN_CENTER);
        header.setVerticalAlignment(Element.ALIGN_MIDDLE);
        header.setPadding(5);
        table.addCell(header);
    }

    private String convertNumberToVietnameseText(long number) {
        // Đây là bản đơn giản, bạn có thể nâng cấp thêm (xử lý hàng chục triệu, tỷ...)
        final String[] units = {"", "mươi", "trăm", "nghìn", "triệu", "tỷ"};
        final String[] digits = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};

        if (number == 0) return "Không";

        StringBuilder sb = new StringBuilder();
        String numStr = Long.toString(number);
        int len = numStr.length();
        boolean isFirst = true;

        for (int i = 0; i < len; i++) {
            int digit = numStr.charAt(i) - '0';
            int pos = len - i - 1;

            if (digit != 0) {
                if (!isFirst) sb.append(" ");
                sb.append(digits[digit]);
                if (pos % 3 == 0 && pos != 0) sb.append(" ").append(units[pos / 3]);
                isFirst = false;
            } else {
                if (i < len - 1 && numStr.charAt(i + 1) != '0') {
                    sb.append(" không");
                }
            }
        }

        // Viết hoa chữ cái đầu
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

}