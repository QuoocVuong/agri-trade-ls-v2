package com.yourcompany.agritrade.ordering.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.InvoiceMapper;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.repository.specification.InvoiceSpecifications;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.yourcompany.agritrade.usermanagement.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final OrderRepository orderRepository;

  private final InvoiceMapper invoiceMapper;

  @Value("${app.invoice.payment_terms_days:7}") // Giá trị mặc định là 30
  private int invoicePaymentTermsDays;

  @Override
  @Transactional
  public Invoice getOrCreateInvoiceForOrder(Order order) {
    return invoiceRepository.findByOrderId(order.getId()).orElseGet(() -> createNewInvoice(order));
  }

  private Invoice createNewInvoice(Order order) {
    Invoice invoice = new Invoice();
    invoice.setOrder(order);
    invoice.setInvoiceNumber(generateInvoiceNumber(order.getOrderCode()));
    invoice.setIssueDate(LocalDate.now());
    invoice.setTotalAmount(order.getTotalAmount());
    // Logic set dueDate, status tùy theo loại đơn/phương thức thanh toán
    if (order.getPaymentMethod() == PaymentMethod.INVOICE) {
      // Ví dụ: Công nợ 30 ngày
      invoice.setDueDate(invoice.getIssueDate().plusDays(invoicePaymentTermsDays));
      invoice.setStatus(InvoiceStatus.ISSUED); // Hóa đơn công nợ được phát hành
    } else {
      // Đối với các phương thức thanh toán khác, hóa đơn có thể là DRAFT hoặc ISSUED ngay
      // và có thể được đánh dấu PAID ngay nếu thanh toán thành công tức thì.
      // Hoặc bạn có thể không tạo Invoice cho các đơn hàng không phải công nợ.
      if (order.getPaymentStatus() == PaymentStatus.PAID) {
        invoice.setStatus(InvoiceStatus.PAID);
      } else {
        invoice.setStatus(InvoiceStatus.ISSUED); // Hoặc DRAFT
      }
    }
    log.info("Creating new invoice {} for order {}. Due date: {}, Status: {}",
            invoice.getInvoiceNumber(), order.getId(), invoice.getDueDate(), invoice.getStatus());
    return invoiceRepository.save(invoice);
  }

  private String generateInvoiceNumber(String orderCode) {

    return "INV-" + orderCode;
  }

  @Override
  @Transactional(readOnly = true) // Chỉ đọc dữ liệu để tạo PDF
  public ByteArrayInputStream generateInvoicePdf(Long invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
    // Lấy chi tiết Order để hiển thị thông tin đầy đủ
    Order order =
        orderRepository
            .findByIdWithDetails(invoice.getOrder().getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Order", "id", invoice.getOrder().getId()));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4); // Tạo document iText/OpenPDF


    try {
      PdfWriter writer = PdfWriter.getInstance(document, out);
      document.open();

      // *** Cần đăng ký và sử dụng Font tiếng Việt ở đây ***
      Font fontTitle =
          FontFactory.getFont(
              "../fonts/font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 18, Font.BOLD);
      Font fontNormal =
          FontFactory.getFont(
              "../fonts/font.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10, Font.NORMAL);

      // --- Header ---
      Paragraph title = new Paragraph("HÓA ĐƠN BÁN HÀNG", fontTitle);
      title.setAlignment(Element.ALIGN_CENTER);
      document.add(title);
      document.add(new Paragraph("Số: " + invoice.getInvoiceNumber(), fontNormal));
      document.add(
          new Paragraph(
              "Ngày xuất: " + invoice.getIssueDate().format(DateTimeFormatter.ISO_DATE),
              fontNormal));
      document.add(Chunk.NEWLINE); // Dòng trống

      // --- Thông tin Người bán / Người mua (Dùng Bảng) ---
      PdfPTable infoTable = new PdfPTable(2); // 2 cột
      infoTable.setWidthPercentage(100);
      infoTable.addCell(createCell("Người bán:", fontNormal, Element.ALIGN_LEFT, false));
      infoTable.addCell(createCell("Người mua:", fontNormal, Element.ALIGN_LEFT, false));
      infoTable.addCell(
          createCell(
              order.getFarmer().getFullName(),
              fontNormal,
              Element.ALIGN_LEFT,
              false)); // Cần font TV
      infoTable.addCell(
          createCell(
              order.getBuyer().getFullName(),
              fontNormal,
              Element.ALIGN_LEFT,
              false)); // Cần font TV
      // Thêm địa chỉ, MST...
      document.add(infoTable);
      document.add(Chunk.NEWLINE);

      // --- Bảng Chi tiết Sản phẩm ---
      PdfPTable itemTable = new PdfPTable(6); // STT, Tên SP, ĐVT, SL, Đơn giá, Thành tiền
      itemTable.setWidthPercentage(100);
      itemTable.setWidths(new float[] {1, 5, 2, 2, 3, 3}); // Tỷ lệ độ rộng cột
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
        itemTable.addCell(
            createCell(String.valueOf(stt++), fontNormal, Element.ALIGN_CENTER, true));
        itemTable.addCell(createCell(item.getProductName(), fontNormal, Element.ALIGN_LEFT, true));
        itemTable.addCell(createCell(item.getUnit(), fontNormal, Element.ALIGN_CENTER, true));
        itemTable.addCell(
            createCell(String.valueOf(item.getQuantity()), fontNormal, Element.ALIGN_RIGHT, true));
        itemTable.addCell(
            createCell(
                item.getPricePerUnit().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));
        itemTable.addCell(
            createCell(
                item.getTotalPrice().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));
      }
      document.add(itemTable);
      document.add(Chunk.NEWLINE);

      // --- Tổng kết hóa đơn ---
      PdfPTable summaryTable = new PdfPTable(2);
      summaryTable.setWidthPercentage(50);
      summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
      summaryTable.setWidths(new float[] {3, 3});

      summaryTable.addCell(createCell("Tạm tính:", fontNormal, Element.ALIGN_LEFT, true));
      summaryTable.addCell(
          createCell(order.getSubTotal().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

      summaryTable.addCell(createCell("Phí vận chuyển:", fontNormal, Element.ALIGN_LEFT, true));
      summaryTable.addCell(
          createCell(
              order.getShippingFee().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

      summaryTable.addCell(createCell("Giảm giá:", fontNormal, Element.ALIGN_LEFT, true));
      summaryTable.addCell(
          createCell(
              order.getDiscountAmount().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

      summaryTable.addCell(createCell("TỔNG CỘNG:", fontNormal, Element.ALIGN_LEFT, true));
      summaryTable.addCell(
          createCell(
              order.getTotalAmount().toPlainString(), fontNormal, Element.ALIGN_RIGHT, true));

      document.add(summaryTable);
      document.add(Chunk.NEWLINE);

      // --- Tổng tiền bằng chữ ---
      String totalAmountText =
          convertNumberToVietnameseText(order.getTotalAmount().longValue()) + " đồng";
      Paragraph totalAmountTextPara =
          new Paragraph("Số tiền bằng chữ: " + totalAmountText, fontNormal);
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
    final String[] digits = {
      "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

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

  // -- Phương Thức Của Admin --

  @Override
  @Transactional(readOnly = true)
  public Page<InvoiceSummaryResponse> getAllInvoicesForAdmin(InvoiceStatus status,  PaymentStatus paymentStatus, String keyword, Pageable pageable) {
    log.debug("Admin fetching invoices with status: {}, keyword: {}, pageable: {}", status, keyword, pageable);

    Specification<Invoice> spec = Specification.where(null); // Bắt đầu với một spec trống

    if (status != null) {
      spec = spec.and(InvoiceSpecifications.hasStatus(status));
    }
    if (paymentStatus != null) spec = spec.and(InvoiceSpecifications.hasOrderPaymentStatus(paymentStatus));

    if (StringUtils.hasText(keyword)) {
      // Tìm kiếm theo invoiceNumber, orderCode, hoặc buyerFullName
      Specification<Invoice> keywordSpec = Specification.anyOf(
              InvoiceSpecifications.hasInvoiceNumber(keyword),
              InvoiceSpecifications.hasOrderCode(keyword),
              InvoiceSpecifications.hasBuyerFullName(keyword)
      );
      spec = spec.and(keywordSpec);
    }

    spec = spec.and(InvoiceSpecifications.fetchOrderAndBuyer());


    Page<Invoice> invoicePage = invoiceRepository.findAll(spec, pageable);
    return invoiceMapper.toInvoiceSummaryResponsePage(invoicePage);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<InvoiceSummaryResponse> getInvoicesForFarmer(Authentication authentication, InvoiceStatus status, PaymentStatus paymentStatus, String keyword, Pageable pageable) {
    User farmer = SecurityUtils.getCurrentAuthenticatedUser(); // Hàm helper của bạn
    Specification<Invoice> spec = Specification.where(InvoiceSpecifications.fetchOrderAndBuyer())
            .and(InvoiceSpecifications.isFarmerInvoice(farmer.getId())); // Spec mới

    if (status != null) spec = spec.and(InvoiceSpecifications.hasStatus(status));
    if (paymentStatus != null) spec = spec.and(InvoiceSpecifications.hasOrderPaymentStatus(paymentStatus));
    if (StringUtils.hasText(keyword)) {
      // Farmer có thể tìm theo mã HĐ, mã ĐH, tên người mua của hóa đơn họ
      spec = spec.and(Specification.anyOf(
              InvoiceSpecifications.hasInvoiceNumber(keyword),
              InvoiceSpecifications.hasOrderCode(keyword),
              InvoiceSpecifications.hasBuyerFullName(keyword) // Vẫn giữ vì farmer cần biết của buyer nào
      ));
    }
    Page<Invoice> invoicePage = invoiceRepository.findAll(spec, pageable);
    return invoiceMapper.toInvoiceSummaryResponsePage(invoicePage);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceSummaryResponse> getInvoicesForBuyer(Authentication authentication, InvoiceStatus status,  PaymentStatus paymentStatus, String keyword, Pageable pageable) {
    User buyer = SecurityUtils.getCurrentAuthenticatedUser();

    Specification<Invoice> spec = Specification.where(InvoiceSpecifications.fetchOrderAndBuyer()) // Fetch thông tin cần thiết
            .and(InvoiceSpecifications.isBuyerInvoice(buyer.getId())); // Spec mới

    if (status != null) {
      spec = spec.and(InvoiceSpecifications.hasStatus(status));
    }
    if (paymentStatus != null) spec = spec.and(InvoiceSpecifications.hasOrderPaymentStatus(paymentStatus));
    if (StringUtils.hasText(keyword)) {
      // Buyer có thể tìm theo mã HĐ hoặc mã ĐH
      spec = spec.and(Specification.anyOf(
              InvoiceSpecifications.hasInvoiceNumber(keyword),
              InvoiceSpecifications.hasOrderCode(keyword)
      ));
    }

    // Chỉ lấy các hóa đơn có phương thức thanh toán là INVOICE
    spec = spec.and(InvoiceSpecifications.hasPaymentMethod(PaymentMethod.INVOICE));


    Page<Invoice> invoicePage = invoiceRepository.findAll(spec, pageable);
    return invoiceMapper.toInvoiceSummaryResponsePage(invoicePage);
  }


}
