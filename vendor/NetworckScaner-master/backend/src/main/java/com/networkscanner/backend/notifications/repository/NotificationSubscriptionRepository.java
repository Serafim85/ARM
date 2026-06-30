package com.networkscanner.backend.notifications.repository;

import com.networkscanner.backend.notifications.model.NotificationSubscriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscriptionEntity, Long> {
  List<NotificationSubscriptionEntity> findByEnabledTrueAndChannelIgnoreCase(String channel);
  List<NotificationSubscriptionEntity> findByOwnerEmailIgnoreCaseOrderByIdAsc(String ownerEmail);
  Optional<NotificationSubscriptionEntity> findByIdAndOwnerEmailIgnoreCase(Long id, String ownerEmail);
}
