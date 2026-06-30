package com.networkscanner.backend.integration.web;

import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.config.WislaIntegrationProperties;
import com.networkscanner.backend.integration.dto.KafkaBrokerMetadataResponse;
import com.networkscanner.backend.integration.dto.KafkaBrokerMetadataTopics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Интеграция Wisla", description = "Metadata endpoint для Kafka broker конфигурации wiSLA")
public class WislaKafkaBrokerMetadataController {

  private static final String METADATA_SCHEMA_VERSION = "1.0";

  private final KafkaProperties kafkaProperties;
  private final MonitoringKafkaProperties monitoringKafkaProperties;
  private final WislaIntegrationProperties wislaIntegrationProperties;

  public WislaKafkaBrokerMetadataController(
      KafkaProperties kafkaProperties,
      MonitoringKafkaProperties monitoringKafkaProperties,
      WislaIntegrationProperties wislaIntegrationProperties
  ) {
    this.kafkaProperties = kafkaProperties;
    this.monitoringKafkaProperties = monitoringKafkaProperties;
    this.wislaIntegrationProperties = wislaIntegrationProperties;
  }

  @GetMapping("${app.integration.wisla.kafka-broker-metadata-path:/api/wisla/kafka-broker-metadata}")
  @Operation(summary = "Возвращает metadata для wiSLA Kafka consumers")
  public KafkaBrokerMetadataResponse kafkaBrokerMetadata() {
    return new KafkaBrokerMetadataResponse(
        toBootstrapServers(kafkaProperties.getBootstrapServers()),
        METADATA_SCHEMA_VERSION,
        new KafkaBrokerMetadataTopics(
            monitoringKafkaProperties.getTopics().getWislaAvailability(),
            monitoringKafkaProperties.getTopics().getWislaIncidents(),
            monitoringKafkaProperties.getTopics().getWislaMonitorStateSnapshot()
        ),
        securityMap(wislaIntegrationProperties.getKafkaBrokerSecurity())
    );
  }

  private static String toBootstrapServers(List<String> servers) {
    if (servers == null || servers.isEmpty()) {
      return "";
    }
    return String.join(",", servers);
  }

  private static Map<String, Object> securityMap(WislaIntegrationProperties.KafkaBrokerSecurity security) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfNotBlank(map, "security.protocol", security.getSecurityProtocol());
    putIfNotBlank(map, "sasl.mechanism", security.getSaslMechanism());
    putIfNotBlank(map, "sasl.jaas.config", security.getSaslJaasConfig());
    putIfNotBlank(map, "ssl.truststore.location", security.getSslTruststoreLocation());
    putIfNotBlank(map, "ssl.truststore.password", security.getSslTruststorePassword());
    putIfNotBlank(map, "ssl.keystore.location", security.getSslKeystoreLocation());
    putIfNotBlank(map, "ssl.keystore.password", security.getSslKeystorePassword());
    putIfNotBlank(map, "ssl.key.password", security.getSslKeyPassword());
    return map;
  }

  private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    map.put(key, value.trim());
  }
}
