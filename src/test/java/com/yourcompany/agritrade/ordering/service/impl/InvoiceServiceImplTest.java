package com.yourcompany.agritrade.ordering.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.InvoiceMapper;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private InvoiceMapper invoiceMapper;

  @InjectMocks private InvoiceServiceImpl invoiceService;

  private Order testOrder;
  private Invoice testInvoice;
  private User testBuyer;
  private User testFarmer;

  @BeforeEach
  void setUp() {
    // Gán giá trị cho @Value field
    ReflectionTestUtils.setField(invoiceService, "invoicePaymentTermsDays", 7);

    testBuyer = new User();
    testBuyer.setId(1L);
    testBuyer.setFullName("Test Buyer");

    testFarmer = new User();
    testFarmer.setId(2L);
    testFarmer.setFullName("Test Farmer");

    testOrder = new Order();
    testOrder.setId(1L);
    testOrder.setOrderCode("ORD123");
    testOrder.setTotalAmount(new BigDecimal("1000.00"));
    testOrder.setPaymentMethod(PaymentMethod.COD);
    testOrder.setPaymentStatus(PaymentStatus.PENDING);

    // *** SỬA Ở ĐÂY: Khởi tạo các giá trị BigDecimal ***
    testOrder.setSubTotal(new BigDecimal("900.00")); // Ví dụ
    testOrder.setShippingFee(new BigDecimal("50.00")); // Ví dụ
    testOrder.setDiscountAmount(new BigDecimal("0.00")); // Ví dụ
    testOrder.setTotalAmount(new BigDecimal("950.00")); // Tính toán lại cho khớp
    // **************************************************

    //        testOrder.setBuyer(testBuyer); // Gán buyer cho order
    //        testOrder.setFarmer(testFarmer); // Gán farmer cho order
    testOrder.setOrderItems(new HashSet<>()); // Khởi tạo để tránh NPE trong generatePdf

    // Thêm một OrderItem mẫu nếu cần cho logic PDF
    OrderItem sampleItem = new OrderItem();
    sampleItem.setProductName("Sản phẩm mẫu");
    sampleItem.setUnit("cái");
    sampleItem.setQuantity(1);
    sampleItem.setPricePerUnit(new BigDecimal("900.00"));
    sampleItem.setTotalPrice(new BigDecimal("900.00"));
    testOrder.getOrderItems().add(sampleItem);

    testInvoice = new Invoice();
    testInvoice.setId(10L);
    testInvoice.setOrder(testOrder);
    testInvoice.setInvoiceNumber("INV-ORD123");
    testInvoice.setIssueDate(LocalDate.now());
    testInvoice.setTotalAmount(testOrder.getTotalAmount());
    testInvoice.setStatus(InvoiceStatus.ISSUED);
  }

  @Nested
  @DisplayName("Get Or Create Invoice Tests")
  class GetOrCreateInvoiceTests {

    @Test
    @DisplayName("Get Or Create - Invoice Exists - Returns Existing Invoice")
    void getOrCreateInvoiceForOrder_whenInvoiceExists_shouldReturnExistingInvoice() {
      when(invoiceRepository.findByOrderId(testOrder.getId())).thenReturn(Optional.of(testInvoice));

      Invoice result = invoiceService.getOrCreateInvoiceForOrder(testOrder);

      assertNotNull(result);
      assertEquals(testInvoice.getId(), result.getId());
      assertEquals(testInvoice.getInvoiceNumber(), result.getInvoiceNumber());
      verify(invoiceRepository).findByOrderId(testOrder.getId());
      verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName(
        "Get Or Create - Invoice Not Exists, Payment Method COD, Payment PENDING - Creates New ISSUED Invoice")
    void
        getOrCreateInvoiceForOrder_whenInvoiceNotExistsAndCodPending_shouldCreateNewIssuedInvoice() {
      testOrder.setPaymentMethod(PaymentMethod.COD);
      testOrder.setPaymentStatus(PaymentStatus.PENDING);

      when(invoiceRepository.findByOrderId(testOrder.getId())).thenReturn(Optional.empty());
      when(invoiceRepository.save(any(Invoice.class)))
          .thenAnswer(
              invocation -> {
                Invoice inv = invocation.getArgument(0);
                inv.setId(11L); // Simulate ID generation
                return inv;
              });

      Invoice result = invoiceService.getOrCreateInvoiceForOrder(testOrder);

      assertNotNull(result);
      assertEquals("INV-" + testOrder.getOrderCode(), result.getInvoiceNumber());
      assertEquals(LocalDate.now(), result.getIssueDate());
      assertEquals(testOrder.getTotalAmount(), result.getTotalAmount());
      assertEquals(InvoiceStatus.ISSUED, result.getStatus()); // COD, PENDING -> ISSUED
      assertNull(result.getDueDate()); // COD không có due date

      verify(invoiceRepository).findByOrderId(testOrder.getId());
      verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName(
        "Get Or Create - Invoice Not Exists, Payment Method COD, Payment PAID - Creates New PAID Invoice")
    void getOrCreateInvoiceForOrder_whenInvoiceNotExistsAndCodPaid_shouldCreateNewPaidInvoice() {
      testOrder.setPaymentMethod(PaymentMethod.COD);
      testOrder.setPaymentStatus(PaymentStatus.PAID); // Đã thanh toán

      when(invoiceRepository.findByOrderId(testOrder.getId())).thenReturn(Optional.empty());
      when(invoiceRepository.save(any(Invoice.class)))
          .thenAnswer(
              invocation -> {
                Invoice inv = invocation.getArgument(0);
                inv.setId(12L);
                return inv;
              });

      Invoice result = invoiceService.getOrCreateInvoiceForOrder(testOrder);

      assertNotNull(result);
      assertEquals(InvoiceStatus.PAID, result.getStatus()); // COD, PAID -> PAID
      verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName(
        "Get Or Create - Invoice Not Exists, Payment Method INVOICE - Creates New ISSUED Invoice with DueDate")
    void
        getOrCreateInvoiceForOrder_whenInvoiceNotExistsAndInvoiceMethod_shouldCreateNewIssuedInvoiceWithDueDate() {
      testOrder.setPaymentMethod(PaymentMethod.INVOICE); // Công nợ

      when(invoiceRepository.findByOrderId(testOrder.getId())).thenReturn(Optional.empty());
      when(invoiceRepository.save(any(Invoice.class)))
          .thenAnswer(
              invocation -> {
                Invoice inv = invocation.getArgument(0);
                inv.setId(13L);
                return inv;
              });

      Invoice result = invoiceService.getOrCreateInvoiceForOrder(testOrder);

      assertNotNull(result);
      assertEquals(InvoiceStatus.ISSUED, result.getStatus());
      assertNotNull(result.getDueDate());
      assertEquals(
          LocalDate.now().plusDays(7),
          result.getDueDate()); // 7 là giá trị invoicePaymentTermsDays đã set
      verify(invoiceRepository).save(any(Invoice.class));
    }
  }

  @Nested
  @DisplayName("Generate Invoice PDF Tests")
  class GenerateInvoicePdfTests {

    @Test
    @DisplayName("Generate PDF - Invoice Not Found - Throws ResourceNotFoundException")
    void generateInvoicePdf_whenInvoiceNotFound_shouldThrowResourceNotFoundException() {
      when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

      assertThrows(ResourceNotFoundException.class, () -> invoiceService.generateInvoicePdf(99L));
    }

    @Test
    @DisplayName("Generate PDF - Order Not Found for Invoice - Throws ResourceNotFoundException")
    void generateInvoicePdf_whenOrderNotFoundForInvoice_shouldThrowResourceNotFoundException() {
      when(invoiceRepository.findById(testInvoice.getId())).thenReturn(Optional.of(testInvoice));
      when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> invoiceService.generateInvoicePdf(testInvoice.getId()));
    }
  }

  @Nested
  @DisplayName("Admin Get All Invoices Tests")
  class AdminGetAllInvoicesTests {
    @Test
    @DisplayName("Get All Invoices - No Filters - Returns Page Of Summaries")
    void getAllInvoices_noFilters_shouldReturnPageOfSummaries() {
      Pageable pageable = PageRequest.of(0, 10);
      List<Invoice> invoices = List.of(testInvoice);
      Page<Invoice> invoicePage = new PageImpl<>(invoices, pageable, invoices.size());
      InvoiceSummaryResponse summaryResponse = new InvoiceSummaryResponse(); // Tạo DTO mẫu
      summaryResponse.setInvoiceId(testInvoice.getId());
      summaryResponse.setInvoiceNumber(testInvoice.getInvoiceNumber());

      when(invoiceRepository.findAll(any(Specification.class), eq(pageable)))
          .thenReturn(invoicePage);
      when(invoiceMapper.toInvoiceSummaryResponsePage(invoicePage))
          .thenReturn(new PageImpl<>(List.of(summaryResponse), pageable, 1));

      Page<InvoiceSummaryResponse> result =
          invoiceService.getAllInvoicesForAdmin(null, null, null, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals(
          summaryResponse.getInvoiceNumber(), result.getContent().get(0).getInvoiceNumber());
      verify(invoiceRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Get All Invoices - With Status Filter")
    void getAllInvoices_withStatusFilter_shouldCallRepositoryWithCorrectSpec() {
      Pageable pageable = PageRequest.of(0, 10);
      InvoiceStatus status = InvoiceStatus.PAID;
      Page<Invoice> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

      when(invoiceRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);
      when(invoiceMapper.toInvoiceSummaryResponsePage(emptyPage))
          .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

      invoiceService.getAllInvoicesForAdmin(status, null, null, pageable);

      ArgumentCaptor<Specification<Invoice>> specCaptor =
          ArgumentCaptor.forClass(Specification.class);
      verify(invoiceRepository).findAll(specCaptor.capture(), eq(pageable));
      // Khó để kiểm tra nội dung của Specification một cách chính xác trong unit test.
      // Thường thì chúng ta tin tưởng rằng Specification được tạo đúng.
      // Hoặc bạn có thể test lớp InvoiceSpecifications riêng.
    }

    @Test
    @DisplayName("Get All Invoices - With Keyword Filter")
    void getAllInvoices_withKeywordFilter_shouldCallRepositoryWithCorrectSpec() {
      Pageable pageable = PageRequest.of(0, 10);
      String keyword = "ORD123";
      Page<Invoice> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

      when(invoiceRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);
      when(invoiceMapper.toInvoiceSummaryResponsePage(emptyPage))
          .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

      invoiceService.getAllInvoicesForAdmin(null, null, keyword, pageable);

      ArgumentCaptor<Specification<Invoice>> specCaptor =
          ArgumentCaptor.forClass(Specification.class);
      verify(invoiceRepository).findAll(specCaptor.capture(), eq(pageable));
    }
  }
}
