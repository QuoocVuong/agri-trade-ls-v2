package com.yourcompany.agritrade.notification.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.notification.domain.Notification;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.mapper.NotificationMapper;
import com.yourcompany.agritrade.notification.repository.NotificationRepository;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.InAppNotificationService;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private NotificationMapper notificationMapper;
  @Mock private EmailService emailService;
  @Mock private InAppNotificationService inAppNotificationService;
  @Mock private UserRepository userRepository;
  @Mock private Authentication authentication;

  @InjectMocks private NotificationServiceImpl notificationService;

  private User testUser, testBuyer, testFarmer, testAdmin;
  private Order testOrder;
  private Product testProduct;
  private Review testReview;
  private FarmerProfile testFarmerProfile;
  private Invoice testInvoice;

  private final String FRONTEND_URL = "http://localhost:4200";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(notificationService, "frontendUrl", FRONTEND_URL);

    testUser = new User();
    testUser.setId(1L);
    testUser.setEmail("user@example.com");
    testUser.setFullName("Test User");

    testBuyer = new User();
    testBuyer.setId(2L);
    testBuyer.setEmail("buyer@example.com");
    testBuyer.setFullName("Test Buyer");

    testFarmer = new User();
    testFarmer.setId(3L);
    testFarmer.setEmail("farmer@example.com");
    testFarmer.setFullName("Test Farmer");

    testAdmin = new User();
    testAdmin.setId(4L);
    testAdmin.setEmail("admin@example.com");
    testAdmin.setFullName("Test Admin");

    testOrder = new Order();
    testOrder.setId(10L);
    testOrder.setOrderCode("ORD123");
    testOrder.setBuyer(testBuyer);
    testOrder.setFarmer(testFarmer);
    testOrder.setStatus(OrderStatus.CONFIRMED);

    testProduct = new Product();
    testProduct.setId(20L);
    testProduct.setName("Awesome Product");
    testProduct.setSlug("awesome-product");

    testReview = new Review();
    testReview.setId(30L);
    testReview.setProduct(testProduct);
    testReview.setConsumer(testBuyer);

    testFarmerProfile = new FarmerProfile();
    testFarmerProfile.setUser(testFarmer);
    testFarmerProfile.setFarmName("Green Farm");

    testInvoice = new Invoice();
    testInvoice.setId(40L);
    testInvoice.setOrder(testOrder);
    testInvoice.setInvoiceNumber("INV-ORD123");
    testInvoice.setDueDate(LocalDate.now().plusDays(5));

    lenient().when(authentication.getName()).thenReturn(testUser.getEmail());
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
    lenient()
        .when(userRepository.findByEmail(testUser.getEmail()))
        .thenReturn(Optional.of(testUser));
  }

  private void mockAuthenticatedUser(User user) {
    when(authentication.getName()).thenReturn(user.getEmail());
    when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
  }

  @Nested
  @DisplayName("Send Notification Tests")
  class SendNotificationTests {

    @Test
    @DisplayName("Send Order Placement Notification")
    void sendOrderPlacementNotification_shouldCallInAppAndEmailServices() {
      notificationService.sendOrderPlacementNotification(testOrder);

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);

      // Verify buyer notification
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testBuyer), messageCaptor.capture(), typeCaptor.capture(), linkCaptor.capture());
      assertTrue(messageCaptor.getValue().contains(testOrder.getOrderCode()));
      assertEquals(NotificationType.ORDER_PLACED, typeCaptor.getValue());
      assertTrue(linkCaptor.getValue().contains("/user/orders/" + testOrder.getId()));
      // verify(emailService).sendOrderConfirmationEmailToBuyer(testOrder); // Uncomment if used

      // Verify farmer notification
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testFarmer), messageCaptor.capture(), typeCaptor.capture(), linkCaptor.capture());
      assertTrue(messageCaptor.getValue().contains(testOrder.getOrderCode()));
      assertTrue(messageCaptor.getValue().contains(testBuyer.getFullName()));
      assertEquals(NotificationType.ORDER_PLACED, typeCaptor.getValue());
      assertTrue(linkCaptor.getValue().contains("/farmer/orders/" + testOrder.getId()));
      // verify(emailService).sendNewOrderNotificationToFarmer(testOrder); // Uncomment if used
    }

    @Test
    @DisplayName("Send Order Status Update Notification")
    void sendOrderStatusUpdateNotification_shouldNotifyBuyerAndFarmerForSpecificStatuses() {
      OrderStatus previousStatus = OrderStatus.PROCESSING;
      testOrder.setStatus(OrderStatus.DELIVERED); // New status

      notificationService.sendOrderStatusUpdateNotification(testOrder, previousStatus);

      // Buyer notification
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testBuyer),
              contains("cập nhật trạng thái thành: DELIVERED"),
              eq(NotificationType.ORDER_STATUS_UPDATE),
              contains("/user/orders/" + testOrder.getId()));
      // verify(emailService).sendOrderStatusUpdateEmailToBuyer(testOrder, previousStatus);

      // Farmer notification (because status is DELIVERED)
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testFarmer),
              contains("cập nhật trạng thái thành: DELIVERED"),
              eq(NotificationType.ORDER_STATUS_UPDATE),
              contains("/farmer/orders/" + testOrder.getId()));
    }

    @Test
    @DisplayName("Send Product Approved Notification - Farmer Null")
    void sendProductApprovedNotification_whenFarmerIsNull_shouldLogAndReturn() {
      // Capture log output if possible, or just verify no interaction with inAppNotificationService
      notificationService.sendProductApprovedNotification(testProduct, null);
      verify(inAppNotificationService, never())
          .createAndSendInAppNotification(any(), anyString(), any(), anyString());
    }

    //        @Test
    //        @DisplayName("Send New Chat Message Notification")
    //        void sendNewChatMessageNotification_shouldCallInAppService() {
    //            notificationService.sendNewChatMessageNotification(testBuyer, testFarmer, 123L);
    // // roomId 123
    //
    //            verify(inAppNotificationService).createAndSendInAppNotification(
    //                    eq(testBuyer),
    //                    contains("tin nhắn mới từ " + testFarmer.getFullName()),
    //                    eq(NotificationType.NEW_MESSAGE),
    //                    eq(FRONTEND_URL + "/chat?roomId=123L")
    //            );
    //        }
  }

  @Nested
  @DisplayName("Manage In-App Notification Tests")
  class ManageInAppNotifications {
    @BeforeEach
    void manageSetup() {
      mockAuthenticatedUser(testUser);
    }

    @Test
    @DisplayName("Get My Notifications - Success")
    void getMyNotifications_success() {
      Pageable pageable = PageRequest.of(0, 10);
      Notification n1 = new Notification(testUser, "Msg1", NotificationType.OTHER, null);
      Page<Notification> notificationPage = new PageImpl<>(List.of(n1), pageable, 1);
      NotificationResponse nr1 = new NotificationResponse(); /* map n1 */

      when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(testUser.getId(), pageable))
          .thenReturn(notificationPage);
      when(notificationMapper.toNotificationResponsePage(notificationPage))
          .thenReturn(new PageImpl<>(List.of(nr1)));

      Page<NotificationResponse> result =
          notificationService.getMyNotifications(authentication, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Get My Unread Notification Count - Success")
    void getMyUnreadNotificationCount_success() {
      when(notificationRepository.countByRecipientIdAndIsReadFalse(testUser.getId()))
          .thenReturn(5L);
      long count = notificationService.getMyUnreadNotificationCount(authentication);
      assertEquals(5L, count);
    }

    @Test
    @DisplayName("Mark Notification As Read - Success")
    void markNotificationAsRead_success() {
      Long notificationId = 1L;
      when(notificationRepository.markAsRead(
              eq(notificationId), eq(testUser.getId()), any(LocalDateTime.class)))
          .thenReturn(1); // 1 row updated

      notificationService.markNotificationAsRead(authentication, notificationId);
      verify(notificationRepository)
          .markAsRead(eq(notificationId), eq(testUser.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Mark Notification As Read - Not Found or Not Owned - Does Not Throw")
    void markNotificationAsRead_notFoundOrNotOwned_doesNotThrow() {
      Long notificationId = 2L;
      when(notificationRepository.markAsRead(
              eq(notificationId), eq(testUser.getId()), any(LocalDateTime.class)))
          .thenReturn(0); // 0 rows updated

      assertDoesNotThrow(
          () -> notificationService.markNotificationAsRead(authentication, notificationId));
      verify(notificationRepository)
          .markAsRead(eq(notificationId), eq(testUser.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Mark All My Notifications As Read - Success")
    void markAllMyNotificationsAsRead_success() {
      when(notificationRepository.markAllAsReadForRecipient(
              eq(testUser.getId()), any(LocalDateTime.class)))
          .thenReturn(3); // 3 rows updated
      notificationService.markAllMyNotificationsAsRead(authentication);
      verify(notificationRepository)
          .markAllAsReadForRecipient(eq(testUser.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Delete Notification - Success")
    void deleteNotification_success() {
      Long notificationId = 1L;
      Notification notification = new Notification(testUser, "Msg", NotificationType.OTHER, null);
      notification.setId(notificationId);

      when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
      doNothing().when(notificationRepository).delete(notification);

      notificationService.deleteNotification(authentication, notificationId);
      verify(notificationRepository).delete(notification);
    }

    @Test
    @DisplayName("Delete Notification - Not Found - Throws ResourceNotFoundException")
    void deleteNotification_notFound_throwsResourceNotFound() {
      Long notificationId = 2L;
      when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
      assertThrows(
          ResourceNotFoundException.class,
          () -> notificationService.deleteNotification(authentication, notificationId));
    }

    @Test
    @DisplayName("Delete Notification - Not Owned - Throws AccessDeniedException")
    void deleteNotification_notOwned_throwsAccessDenied() {
      Long notificationId = 3L;
      User anotherUser = new User();
      anotherUser.setId(99L);
      Notification notification =
          new Notification(anotherUser, "Msg", NotificationType.OTHER, null);
      notification.setId(notificationId);

      when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
      assertThrows(
          AccessDeniedException.class,
          () -> notificationService.deleteNotification(authentication, notificationId));
    }
  }

  @Nested
  @DisplayName("User Event Notifications")
  class UserEventNotifications {
    @Test
    @DisplayName("Send Welcome Notification")
    void sendWelcomeNotification_callsEmailAndInAppServices() {
      notificationService.sendWelcomeNotification(testUser);
      verify(emailService).sendWelcomeEmail(testUser);
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testUser),
              contains("Chào mừng bạn đến với AgriTradeLS"),
              eq(NotificationType.WELCOME),
              eq(FRONTEND_URL));
    }

    // ... Thêm test cho sendPasswordChangedNotification, sendAccountStatusUpdateNotification,
    // sendRolesUpdateNotification ...
  }

  @Nested
  @DisplayName("Interaction Event Notifications")
  class InteractionEventNotifications {
    @Test
    @DisplayName("Send New Follower Notification")
    void sendNewFollowerNotification_callsInAppService() {
      notificationService.sendNewFollowerNotification(
          testFarmer, testBuyer); // Buyer follows Farmer
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testFarmer),
              contains(testBuyer.getFullName() + "</strong> đã bắt đầu theo dõi bạn"),
              eq(NotificationType.NEW_FOLLOWER),
              eq(FRONTEND_URL + "/profile/" + testBuyer.getId()));
    }

    @Test
    @DisplayName("Send Review Approved Notification")
    void sendReviewApprovedNotification_callsInAppService() {
      notificationService.sendReviewApprovedNotification(testReview);
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testBuyer), // Consumer của review
              contains(
                  "Đánh giá của bạn cho sản phẩm '" + testProduct.getName() + "' đã được duyệt"),
              eq(NotificationType.REVIEW_APPROVED),
              eq(FRONTEND_URL + "/products/" + testProduct.getSlug()));
    }
  }

  @Nested
  @DisplayName("Farmer Profile Event Notifications")
  class FarmerProfileEventNotifications {
    @Test
    @DisplayName("Send Farmer Profile Approved Notification - Profile Null")
    void sendFarmerProfileApprovedNotification_whenProfileNull_shouldLogAndReturn() {
      notificationService.sendFarmerProfileApprovedNotification(null);
      verify(inAppNotificationService, never())
          .createAndSendInAppNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Send Farmer Profile Approved Notification - User Null")
    void sendFarmerProfileApprovedNotification_whenUserNull_shouldLogAndReturn() {
      FarmerProfile profileWithNullUser = new FarmerProfile();
      profileWithNullUser.setUser(null); // User là null
      notificationService.sendFarmerProfileApprovedNotification(profileWithNullUser);
      verify(inAppNotificationService, never())
          .createAndSendInAppNotification(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Invoice Event Notifications")
  class InvoiceEventNotifications {
    @Test
    @DisplayName("Send Overdue Invoice Reminder To Buyer")
    void sendOverdueInvoiceReminderToBuyer_callsInAppService() {
      notificationService.sendOverdueInvoiceReminderToBuyer(testInvoice);
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testBuyer),
              contains("Hóa đơn #" + testInvoice.getInvoiceNumber()),
              eq(NotificationType.INVOICE_OVERDUE),
              contains("/user/orders/" + testOrder.getId()));
    }

    @Test
    @DisplayName("Send Overdue Invoice Notification To Admin - No Admins Found")
    void sendOverdueInvoiceNotificationToAdmin_whenNoAdmins_shouldLogAndReturn() {
      when(userRepository.findByRoles_Name(RoleType.ROLE_ADMIN))
          .thenReturn(Collections.emptyList());
      notificationService.sendOverdueInvoiceNotificationToAdmin(testInvoice);
      verify(inAppNotificationService, never())
          .createAndSendInAppNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Send Overdue Invoice Notification To Admin - Success")
    void sendOverdueInvoiceNotificationToAdmin_success() {
      when(userRepository.findByRoles_Name(RoleType.ROLE_ADMIN)).thenReturn(List.of(testAdmin));
      notificationService.sendOverdueInvoiceNotificationToAdmin(testInvoice);
      verify(inAppNotificationService)
          .createAndSendInAppNotification(
              eq(testAdmin),
              contains("CẢNH BÁO: Hóa đơn #" + testInvoice.getInvoiceNumber()),
              eq(NotificationType.ADMIN_ALERT),
              contains("/admin/invoices?invoiceNumber=" + testInvoice.getInvoiceNumber()));
    }
  }
}
