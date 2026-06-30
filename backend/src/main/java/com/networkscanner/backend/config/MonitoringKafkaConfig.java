package com.networkscanner.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringMetricsPublisher;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(MonitoringKafkaProperties.class)
public class MonitoringKafkaConfig {
  private static final Logger log = LoggerFactory.getLogger(MonitoringKafkaConfig.class);

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public KafkaAdmin.NewTopics monitoringTopics(MonitoringKafkaProperties properties) {
    return new KafkaAdmin.NewTopics(
        largeMetricsTopic(properties.getTopics().getPolled(), properties),
        largeMetricsTopic(properties.getTopics().getEvaluated(), properties),
        topic(properties.getTopics().getWislaAvailability(), properties),
        topic(properties.getTopics().getWislaIncidents(), properties),
        monitorStateSnapshotTopic(properties),
        largeMetricsTopic(properties.getTopics().getPolled() + ".DLT", properties),
        largeMetricsTopic(properties.getTopics().getEvaluated() + ".DLT", properties),
        topic(properties.getTopics().getWislaAvailability() + ".DLT", properties),
        topic(properties.getTopics().getWislaIncidents() + ".DLT", properties),
        topic(properties.getTopics().getWislaMonitorStateSnapshot() + ".DLT", properties)
    );
  }

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public ProducerFactory<String, Object> monitoringProducerFactory(
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper
  ) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
    props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(
        props,
        new StringSerializer(),
        new JsonSerializer<>(objectMapper)
    );
  }

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public KafkaTemplate<String, Object> monitoringKafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public CommonErrorHandler monitoringKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
    );
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L)) {
      @Override
      public void handleOtherException(
          Exception thrownException,
          Consumer<?, ?> consumer,
          MessageListenerContainer container,
          boolean batchListener
      ) {
        if (thrownException instanceof TimeoutException) {
          log.warn("Kafka non-record timeout handled without DLT: {}", thrownException.getMessage());
          return;
        }
        super.handleOtherException(thrownException, consumer, container, batchListener);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(MonitoringMetricsPublisher.class)
  public MonitoringMetricsPublisher noopMonitoringMetricsPublisher() {
    return event -> {
      // Kafka pipeline is disabled; collector will continue with synchronous processing.
    };
  }

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public KafkaListenerContainerFactory<?> polledKafkaListenerContainerFactory(
      KafkaProperties kafkaProperties,
      MonitoringKafkaProperties properties,
      ObjectMapper objectMapper,
      CommonErrorHandler errorHandler
  ) {
    return listenerFactory(
        kafkaProperties,
        objectMapper,
        properties.getGroups().getEvaluator(),
        PolledMetricsEvent.class,
        properties,
        errorHandler,
        false
    );
  }

  @Bean
  @ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
  public KafkaListenerContainerFactory<?> evaluatedKafkaListenerContainerFactory(
      KafkaProperties kafkaProperties,
      MonitoringKafkaProperties properties,
      ObjectMapper objectMapper,
      CommonErrorHandler errorHandler
  ) {
    return listenerFactory(
        kafkaProperties,
        objectMapper,
        properties.getGroups().getWriter(),
        EvaluatedMonitoringEvent.class,
        properties,
        errorHandler,
        true
    );
  }

  private NewTopic topic(String name, MonitoringKafkaProperties properties) {
    return new NewTopic(name, properties.getPartitions(), properties.getReplicationFactor());
  }

  private NewTopic largeMetricsTopic(String name, MonitoringKafkaProperties properties) {
    NewTopic topic = new NewTopic(name, properties.getPartitions(), properties.getReplicationFactor());
    Map<String, String> configs = new HashMap<>();
    configs.put(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, Integer.toString(properties.getMaxMessageBytes()));
    topic.configs(configs);
    return topic;
  }

  private NewTopic monitorStateSnapshotTopic(MonitoringKafkaProperties properties) {
    NewTopic topic = new NewTopic(
        properties.getTopics().getWislaMonitorStateSnapshot(),
        properties.getPartitions(),
        properties.getReplicationFactor()
    );
    Map<String, String> configs = new HashMap<>();
    configs.put(
        TopicConfig.RETENTION_BYTES_CONFIG,
        Long.toString(properties.getMonitorStateSnapshot().getRetentionBytes())
    );
    configs.put(
        TopicConfig.RETENTION_MS_CONFIG,
        Long.toString(properties.getMonitorStateSnapshot().getRetentionMs())
    );
    topic.configs(configs);
    return topic;
  }

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper,
      String groupId,
      Class<T> payloadType,
      MonitoringKafkaProperties properties,
      CommonErrorHandler errorHandler,
      boolean batchListener
  ) {
    ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory(kafkaProperties, objectMapper, groupId, payloadType));
    factory.setConcurrency(Math.max(1, properties.getListenerConcurrency()));
    factory.setBatchListener(batchListener);
    factory.getContainerProperties().setAckMode(
        batchListener ? ContainerProperties.AckMode.BATCH : ContainerProperties.AckMode.RECORD
    );
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }

  private <T> ConsumerFactory<String, T> consumerFactory(
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper,
      String groupId,
      Class<T> payloadType
  ) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(payloadType, objectMapper, false);
    valueDeserializer.addTrustedPackages("com.networkscanner.backend.monitoring.dto");
    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
  }
}
