package com.networkscanner.backend.integration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.config.WislaIntegrationProperties;
import com.networkscanner.backend.integration.dto.KafkaBrokerMetadataResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class WislaKafkaBrokerMetadataControllerTest {

  @Test
  void kafkaBrokerMetadataReturnsTopicsAndWhitelistedSecurity() {
    KafkaProperties kafkaProperties = new KafkaProperties();
    kafkaProperties.setBootstrapServers(List.of("k1:9092", "k2:9092"));

    MonitoringKafkaProperties monitoringKafkaProperties = new MonitoringKafkaProperties();
    monitoringKafkaProperties.getTopics().setWislaAvailability("wisla.availability");
    monitoringKafkaProperties.getTopics().setWislaIncidents("wisla.incidents");
    monitoringKafkaProperties.getTopics().setWislaMonitorStateSnapshot("wisla.monitor-state");

    WislaIntegrationProperties integrationProperties = new WislaIntegrationProperties();
    integrationProperties.getKafkaBrokerSecurity().setSecurityProtocol("SASL_SSL");
    integrationProperties.getKafkaBrokerSecurity().setSaslMechanism("SCRAM-SHA-512");
    integrationProperties.getKafkaBrokerSecurity().setSaslJaasConfig("jaas-config");

    WislaKafkaBrokerMetadataController controller = new WislaKafkaBrokerMetadataController(
        kafkaProperties,
        monitoringKafkaProperties,
        integrationProperties
    );

    KafkaBrokerMetadataResponse response = controller.kafkaBrokerMetadata();

    assertEquals("k1:9092,k2:9092", response.bootstrapServers());
    assertEquals("1.0", response.schemaVersion());
    assertEquals("wisla.availability", response.topics().availability());
    assertEquals("wisla.incidents", response.topics().incidents());
    assertEquals("wisla.monitor-state", response.topics().monitoringState());
    assertEquals("SASL_SSL", response.security().get("security.protocol"));
    assertEquals("SCRAM-SHA-512", response.security().get("sasl.mechanism"));
    assertEquals("jaas-config", response.security().get("sasl.jaas.config"));
  }

  @Test
  void kafkaBrokerMetadataOmitsEmptySecurityKeys() {
    KafkaProperties kafkaProperties = new KafkaProperties();
    kafkaProperties.setBootstrapServers(List.of("k1:9092"));

    WislaKafkaBrokerMetadataController controller = new WislaKafkaBrokerMetadataController(
        kafkaProperties,
        new MonitoringKafkaProperties(),
        new WislaIntegrationProperties()
    );

    KafkaBrokerMetadataResponse response = controller.kafkaBrokerMetadata();

    assertTrue(response.security().isEmpty());
    assertFalse(response.security().containsKey("ssl.key.password"));
  }
}
