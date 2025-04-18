package com.yourcompany.agritradels.notification.mapper;

import com.yourcompany.agritradels.notification.domain.Notification;
import com.yourcompany.agritradels.notification.dto.response.NotificationResponse;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page; // Import Page

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toNotificationResponse(Notification notification);

    List<NotificationResponse> toNotificationResponseList(List<Notification> notifications);

    // Map Page<Notification> sang Page<NotificationResponse>
    default Page<NotificationResponse> toNotificationResponsePage(Page<Notification> notificationPage) {
        return notificationPage.map(this::toNotificationResponse);
    }
}