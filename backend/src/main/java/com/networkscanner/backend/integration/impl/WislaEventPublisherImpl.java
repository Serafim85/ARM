package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.integration.api.WislaEventPublisher;
import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "app.integration.wisla-events.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WislaEventPublisherImpl implements WislaEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(WislaEventPublisherImpl.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final MonitoringKafkaProperties kafkaProperties;
  private final AuditLogService auditLogService;
  private final long sendTimeoutMs;

  public WislaEventPublisherImpl(
      KafkaTemplate<String, Object> kafkaTemplate,
      MonitoringKafkaProperties kafkaProperties,
      AuditLogService auditLogService
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaProperties = kafkaProperties;
    this.auditLogService = auditLogService;
    this.sendTimeoutMs = Math.max(250L, kafkaProperties.getPublisher().getSendTimeoutMs());
  }

  @Override
  public void publishAvailability(ProbeAvailabilityUpdate event) {
    send(kafkaProperties.getTopics().getWislaAvailability(), key(event.sourceSystem(), event.externalDeviceId()), event);
  }

  @Override
  public void publishIncident(ExternalIncidentUpsert event) {
    send(kafkaProperties.getTopics().getWislaIncidents(), key(event.sourceSystem(), event.externalDeviceId()), event);
  }

  @Override
  public void publishMonitorStateSnapshot(MonitorStateSnapshot event) {
    send(kafkaProperties.getTopics().getWislaMonitorStateSnapshot(), key(event.sourceSystem(), event.externalDeviceId()), event);
  }

  private void send(String topic, String partitionKey, Object payload) {
    try {
      kafkaTemplate.send(topic, partitionKey, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      log.warn("Failed to publish Wisla event topic={}, key={}, error={}", topic, partitionKey, ex.toString());
      auditLogService.recordForActor(
          "system",
          AuditCategory.WISLA_INTEGRATION,
          AuditAction.INTEGRATION_PUBLISH_FAILED,
          topic + ":" + partitionKey,
          ex.toString()
      );
    }
  }

  private String key(String sourceSystem, Long externalDeviceId) {
    return sourceSystem + "|" + externalDeviceId;
  }
}
