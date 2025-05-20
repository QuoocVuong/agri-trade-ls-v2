package com.yourcompany.agritrade.notification.mapper;

import com.yourcompany.agritrade.notification.domain.Notification;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

  NotificationResponse toNotificationResponse(Notification notification);

  List<NotificationResponse> toNotificationResponseList(List<Notification> notifications);

  // Map Page<Notification> sang Page<NotificationResponse>
  default Page<NotificationResponse> toNotificationResponsePage(
      Page<Notification> notificationPage) {
    return notificationPage.map(this::toNotificationResponse);
  }
}
