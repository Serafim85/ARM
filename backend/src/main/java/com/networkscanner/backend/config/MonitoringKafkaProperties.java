package com.networkscanner.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitoring.kafka")
public class MonitoringKafkaProperties {

  private boolean enabled;
  private boolean evaluatorEnabled = true;
  private boolean writerEnabled = true;
  private int partitions = 6;
  private short replicationFactor = 1;
  private int listenerConcurrency = 3;
  /** Aligns with broker message.max.bytes and topic max.message.bytes (default 2 MiB). */
  private int maxMessageBytes = 2_097_152;
  private final Topics topics = new Topics();
  private final Groups groups = new Groups();
  private final Cache cache = new Cache();
  private final Publisher publisher = new Publisher();
  private final TopicRetention monitorStateSnapshot = new TopicRetention();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEvaluatorEnabled() {
    return evaluatorEnabled;
  }

  public void setEvaluatorEnabled(boolean evaluatorEnabled) {
    this.evaluatorEnabled = evaluatorEnabled;
  }

  public boolean isWriterEnabled() {
    return writerEnabled;
  }

  public void setWriterEnabled(boolean writerEnabled) {
    this.writerEnabled = writerEnabled;
  }

  public int getPartitions() {
    return partitions;
  }

  public void setPartitions(int partitions) {
    this.partitions = partitions;
  }

  public short getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(short replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getListenerConcurrency() {
    return listenerConcurrency;
  }

  public void setListenerConcurrency(int listenerConcurrency) {
    this.listenerConcurrency = listenerConcurrency;
  }

  public int getMaxMessageBytes() {
    return maxMessageBytes;
  }

  public void setMaxMessageBytes(int maxMessageBytes) {
    this.maxMessageBytes = maxMessageBytes;
  }

  public Topics getTopics() {
    return topics;
  }

  public Groups getGroups() {
    return groups;
  }

  public Cache getCache() {
    return cache;
  }

  public Publisher getPublisher() {
    return publisher;
  }

  public TopicRetention getMonitorStateSnapshot() {
    return monitorStateSnapshot;
  }

  public static class Topics {
    private String polled = "monitoring.polled";
    private String evaluated = "monitoring.evaluated";
    private String wislaAvailability = "wisla.availability";
    private String wislaIncidents = "wisla.incidents";
    private String wislaMonitorStateSnapshot = "wisla.monitor-state";

    public String getPolled() {
      return polled;
    }

    public void setPolled(String polled) {
      this.polled = polled;
    }

    public String getEvaluated() {
      return evaluated;
    }

    public void setEvaluated(String evaluated) {
      this.evaluated = evaluated;
    }

    public String getWislaAvailability() {
      return wislaAvailability;
    }

    public void setWislaAvailability(String wislaAvailability) {
      this.wislaAvailability = wislaAvailability;
    }

    public String getWislaIncidents() {
      return wislaIncidents;
    }

    public void setWislaIncidents(String wislaIncidents) {
      this.wislaIncidents = wislaIncidents;
    }

    public String getWislaMonitorStateSnapshot() {
      return wislaMonitorStateSnapshot;
    }

    public void setWislaMonitorStateSnapshot(String wislaMonitorStateSnapshot) {
      this.wislaMonitorStateSnapshot = wislaMonitorStateSnapshot;
    }
  }

  public static class Groups {
    private String evaluator = "cg_eval";
    private String writer = "cg_writer";

    public String getEvaluator() {
      return evaluator;
    }

    public void setEvaluator(String evaluator) {
      this.evaluator = evaluator;
    }

    public String getWriter() {
      return writer;
    }

    public void setWriter(String writer) {
      this.writer = writer;
    }
  }

  public static class Cache {
    private int maxDevices = 10000;
    private int expireAfterMinutes = 30;

    public int getMaxDevices() {
      return maxDevices;
    }

    public void setMaxDevices(int maxDevices) {
      this.maxDevices = maxDevices;
    }

    public int getExpireAfterMinutes() {
      return expireAfterMinutes;
    }

    public void setExpireAfterMinutes(int expireAfterMinutes) {
      this.expireAfterMinutes = expireAfterMinutes;
    }
  }

  public static class Publisher {
    private long sendTimeoutMs = 2000L;
    /** Max serialized JSON size per Kafka record (bytes). Chunks are emitted when exceeded. */
    private int maxRecordBytes = 900_000;

    public long getSendTimeoutMs() {
      return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
      this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getMaxRecordBytes() {
      return maxRecordBytes;
    }

    public void setMaxRecordBytes(int maxRecordBytes) {
      this.maxRecordBytes = maxRecordBytes;
    }
  }

  /** Per-topic retention overrides applied at topic creation time. */
  public static class TopicRetention {
    /** retention.bytes (Kafka). Default 1 GiB. */
    private long retentionBytes = 1_073_741_824L;
    /** retention.ms (Kafka). Default 14 days. */
    private long retentionMs = 14L * 24L * 60L * 60L * 1000L;

    public long getRetentionBytes() {
      return retentionBytes;
    }

    public void setRetentionBytes(long retentionBytes) {
      this.retentionBytes = retentionBytes;
    }

    public long getRetentionMs() {
      return retentionMs;
    }

    public void setRetentionMs(long retentionMs) {
      this.retentionMs = retentionMs;
    }
  }
}
