package com.networkscanner.backend.notifications.api;

import com.networkscanner.backend.notifications.dto.NotificationSubscriptionDto;
import com.networkscanner.backend.notifications.dto.SmtpSettingsDto;
import com.networkscanner.backend.notifications.dto.TestNotificationEventRequest;
import com.networkscanner.backend.notifications.dto.UpdateSmtpSettingsRequest;
import com.networkscanner.backend.notifications.dto.UpsertNotificationSubscriptionRequest;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface SystemNotificationSettingsService {
  SmtpSettingsDto getSmtpSettings();
  SmtpSettingsDto updateSmtpSettings(UpdateSmtpSettingsRequest request);
  List<NotificationSubscriptionDto> listSubscriptions(Authentication authentication);
  NotificationSubscriptionDto upsertSubscription(UpsertNotificationSubscriptionRequest request, Authentication authentication);
  void deleteSubscription(long id, Authentication authentication);
  void triggerTestNotificationEvent(TestNotificationEventRequest request, Authentication authentication);
}
