package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoredDeviceRepository
    extends JpaRepository<MonitoredDeviceEntity, Long>, JpaSpecificationExecutor<MonitoredDeviceEntity> {

  Optional<MonitoredDeviceEntity> findByIp(String ip);

  Optional<MonitoredDeviceEntity> findFirstByIpOrderByUpdatedAtDesc(String ip);

  Optional<MonitoredDeviceEntity> findFirstByIpAndSnmpPort(String ip, Integer snmpPort);

  Optional<MonitoredDeviceEntity> findFirstByIpAndSnmpPortIsNull(String ip);

  Optional<MonitoredDeviceEntity> findFirstBySerialNumberIgnoreCase(String serialNumber);

  Optional<MonitoredDeviceEntity> findFirstByMacAddressIgnoreCase(String macAddress);

  List<MonitoredDeviceEntity> findAllByIpIn(Collection<String> ips);

  @Query(
      value = """
          SELECT COUNT(*)
          FROM monitored_devices md
          WHERE md.template_id = :templateId
             OR md.effective_template_id = :templateId
             OR EXISTS (
                SELECT 1
                FROM unnest(string_to_array(COALESCE(md.template_ids, ''), ',')) AS t(template_id)
                WHERE btrim(t.template_id) = :templateId
             )
          """,
      nativeQuery = true
  )
  long countTemplateUsage(@Param("templateId") String templateId);

  void deleteByIpIn(Collection<String> ips);
}
