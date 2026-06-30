package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntityId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredDeviceItemRepository
    extends JpaRepository<MonitoredDeviceItemEntity, MonitoredDeviceItemEntityId> {

  List<MonitoredDeviceItemEntity> findByDeviceId(Long deviceId);

  long countByDeviceId(Long deviceId);

  void deleteByDeviceId(Long deviceId);

  void deleteByDeviceIdAndItemUuidAndInstanceKey(Long deviceId, String itemUuid, String instanceKey);

  void deleteByDeviceIdAndItemUuidIn(Long deviceId, Collection<String> itemUuids);
}
