package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoringEventRepository extends JpaRepository<MonitoringEventEntity, Long> {

  List<MonitoringEventEntity> findByDevice_IdAndStatus(Long deviceId, MonitoringEventStatus status);

  List<MonitoringEventEntity> findByDevice_IdOrderByBreachStartedAtDesc(Long deviceId);

  void deleteByDevice_IdIn(List<Long> deviceIds);

  Optional<MonitoringEventEntity> findFirstByDevice_IdAndMetricNameAndThresholdLevelAndStatus(
      Long deviceId,
      String metricName,
      ThresholdLevel thresholdLevel,
      MonitoringEventStatus status
  );

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = """
          UPDATE monitoring_events
          SET status = 'RESOLVED',
              normalized_at = :normalizedAt
          WHERE device_id = :deviceId
            AND status = 'OPEN'
            AND metric_name = :itemKey
            AND COALESCE(instance_key, '') = COALESCE(:instanceKey, '')
          """,
      nativeQuery = true
  )
  int resolveOpenEventsByItem(
      @Param("deviceId") Long deviceId,
      @Param("itemKey") String itemKey,
      @Param("instanceKey") String instanceKey,
      @Param("normalizedAt") OffsetDateTime normalizedAt
  );

  @Query(
      value = """
          SELECT e.* FROM monitoring_events e
          INNER JOIN monitored_devices d ON e.device_id = d.id
          WHERE (CAST(:status AS text) IS NULL OR e.status = CAST(:status AS text))
          AND (CAST(:deviceId AS bigint) IS NULL OR e.device_id = CAST(:deviceId AS bigint))
          AND (CAST(:thresholdLevel AS text) IS NULL OR e.threshold_level = CAST(:thresholdLevel AS text))
          AND (CAST(:breachStartedFrom AS timestamptz) IS NULL
              OR e.breach_started_at >= CAST(:breachStartedFrom AS timestamptz))
          AND (CAST(:breachStartedTo AS timestamptz) IS NULL
              OR e.breach_started_at <= CAST(:breachStartedTo AS timestamptz))
          AND (CAST(:normalizedFrom AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at >= CAST(:normalizedFrom AS timestamptz)))
          AND (CAST(:normalizedTo AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at <= CAST(:normalizedTo AS timestamptz)))
          AND (CAST(:minDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) >= CAST(:minDurationSeconds AS bigint))
          AND (CAST(:maxDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) <= CAST(:maxDurationSeconds AS bigint))
          AND (CAST(:metricNamePattern AS text) IS NULL OR e.metric_name ILIKE CAST(:metricNamePattern AS text))
          AND (CAST(:macAddressPattern AS text) IS NULL OR d.mac_address ILIKE CAST(:macAddressPattern AS text))
          AND (CAST(:deviceIpPattern AS text) IS NULL OR d.ip ILIKE CAST(:deviceIpPattern AS text))
          AND (CAST(:deviceNamePattern AS text) IS NULL OR d.name ILIKE CAST(:deviceNamePattern AS text))
          AND (CAST(:deviceIdsCsv AS text) IS NULL
              OR e.device_id = ANY(string_to_array(:deviceIdsCsv, ',')::bigint[]))
          AND (CAST(:deviceTagsCsv AS text) IS NULL OR EXISTS (
              SELECT 1
              FROM unnest(string_to_array(:deviceTagsCsv, ',')) AS tag_row(tag)
              WHERE lower(d.tags_json) LIKE '%' || lower(trim(tag_row.tag)) || '%' ESCAPE '\\'
          ))
          ORDER BY e.breach_started_at DESC
          """,
      countQuery = """
          SELECT count(*) FROM monitoring_events e
          INNER JOIN monitored_devices d ON e.device_id = d.id
          WHERE (CAST(:status AS text) IS NULL OR e.status = CAST(:status AS text))
          AND (CAST(:deviceId AS bigint) IS NULL OR e.device_id = CAST(:deviceId AS bigint))
          AND (CAST(:thresholdLevel AS text) IS NULL OR e.threshold_level = CAST(:thresholdLevel AS text))
          AND (CAST(:breachStartedFrom AS timestamptz) IS NULL
              OR e.breach_started_at >= CAST(:breachStartedFrom AS timestamptz))
          AND (CAST(:breachStartedTo AS timestamptz) IS NULL
              OR e.breach_started_at <= CAST(:breachStartedTo AS timestamptz))
          AND (CAST(:normalizedFrom AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at >= CAST(:normalizedFrom AS timestamptz)))
          AND (CAST(:normalizedTo AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at <= CAST(:normalizedTo AS timestamptz)))
          AND (CAST(:minDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) >= CAST(:minDurationSeconds AS bigint))
          AND (CAST(:maxDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) <= CAST(:maxDurationSeconds AS bigint))
          AND (CAST(:metricNamePattern AS text) IS NULL OR e.metric_name ILIKE CAST(:metricNamePattern AS text))
          AND (CAST(:macAddressPattern AS text) IS NULL OR d.mac_address ILIKE CAST(:macAddressPattern AS text))
          AND (CAST(:deviceIpPattern AS text) IS NULL OR d.ip ILIKE CAST(:deviceIpPattern AS text))
          AND (CAST(:deviceNamePattern AS text) IS NULL OR d.name ILIKE CAST(:deviceNamePattern AS text))
          AND (CAST(:deviceIdsCsv AS text) IS NULL
              OR e.device_id = ANY(string_to_array(:deviceIdsCsv, ',')::bigint[]))
          AND (CAST(:deviceTagsCsv AS text) IS NULL OR EXISTS (
              SELECT 1
              FROM unnest(string_to_array(:deviceTagsCsv, ',')) AS tag_row(tag)
              WHERE lower(d.tags_json) LIKE '%' || lower(trim(tag_row.tag)) || '%' ESCAPE '\\'
          ))
          """,
      nativeQuery = true
  )
  Page<MonitoringEventEntity> searchEvents(
      @Param("status") String status,
      @Param("deviceId") Long deviceId,
      @Param("thresholdLevel") String thresholdLevel,
      @Param("breachStartedFrom") OffsetDateTime breachStartedFrom,
      @Param("breachStartedTo") OffsetDateTime breachStartedTo,
      @Param("normalizedFrom") OffsetDateTime normalizedFrom,
      @Param("normalizedTo") OffsetDateTime normalizedTo,
      @Param("minDurationSeconds") Long minDurationSeconds,
      @Param("maxDurationSeconds") Long maxDurationSeconds,
      @Param("metricNamePattern") String metricNamePattern,
      @Param("macAddressPattern") String macAddressPattern,
      @Param("deviceIpPattern") String deviceIpPattern,
      @Param("deviceNamePattern") String deviceNamePattern,
      @Param("deviceIdsCsv") String deviceIdsCsv,
      @Param("deviceTagsCsv") String deviceTagsCsv,
      Pageable pageable
  );

  @Query(
      value = """
          SELECT
              COUNT(*) FILTER (WHERE e.threshold_level = 'DISASTER'),
              COUNT(*) FILTER (WHERE e.threshold_level = 'HIGH'),
              COUNT(*) FILTER (WHERE e.threshold_level = 'AVERAGE'),
              COUNT(*) FILTER (WHERE e.threshold_level = 'WARNING'),
              COUNT(*) FILTER (WHERE e.threshold_level = 'INFORMATION'),
              COUNT(*) FILTER (WHERE e.threshold_level = 'NOT_CLASSIFIED')
          FROM monitoring_events e
          INNER JOIN monitored_devices d ON e.device_id = d.id
          WHERE (CAST(:status AS text) IS NULL OR e.status = CAST(:status AS text))
          AND (CAST(:deviceId AS bigint) IS NULL OR e.device_id = CAST(:deviceId AS bigint))
          AND (CAST(:thresholdLevel AS text) IS NULL OR e.threshold_level = CAST(:thresholdLevel AS text))
          AND (CAST(:breachStartedFrom AS timestamptz) IS NULL
              OR e.breach_started_at >= CAST(:breachStartedFrom AS timestamptz))
          AND (CAST(:breachStartedTo AS timestamptz) IS NULL
              OR e.breach_started_at <= CAST(:breachStartedTo AS timestamptz))
          AND (CAST(:normalizedFrom AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at >= CAST(:normalizedFrom AS timestamptz)))
          AND (CAST(:normalizedTo AS timestamptz) IS NULL
              OR (e.normalized_at IS NOT NULL AND e.normalized_at <= CAST(:normalizedTo AS timestamptz)))
          AND (CAST(:minDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) >= CAST(:minDurationSeconds AS bigint))
          AND (CAST(:maxDurationSeconds AS bigint) IS NULL OR EXTRACT(EPOCH FROM (COALESCE(e.normalized_at, CURRENT_TIMESTAMP)
              - e.breach_started_at)) <= CAST(:maxDurationSeconds AS bigint))
          AND (CAST(:metricNamePattern AS text) IS NULL OR e.metric_name ILIKE CAST(:metricNamePattern AS text))
          AND (CAST(:macAddressPattern AS text) IS NULL OR d.mac_address ILIKE CAST(:macAddressPattern AS text))
          AND (CAST(:deviceIpPattern AS text) IS NULL OR d.ip ILIKE CAST(:deviceIpPattern AS text))
          AND (CAST(:deviceNamePattern AS text) IS NULL OR d.name ILIKE CAST(:deviceNamePattern AS text))
          AND (CAST(:deviceIdsCsv AS text) IS NULL
              OR e.device_id = ANY(string_to_array(:deviceIdsCsv, ',')::bigint[]))
          AND (CAST(:deviceTagsCsv AS text) IS NULL OR EXISTS (
              SELECT 1
              FROM unnest(string_to_array(:deviceTagsCsv, ',')) AS tag_row(tag)
              WHERE lower(d.tags_json) LIKE '%' || lower(trim(tag_row.tag)) || '%' ESCAPE '\\'
          ))
          """,
      nativeQuery = true
  )
  List<Object[]> aggregateEventLevelCounts(
      @Param("status") String status,
      @Param("deviceId") Long deviceId,
      @Param("thresholdLevel") String thresholdLevel,
      @Param("breachStartedFrom") OffsetDateTime breachStartedFrom,
      @Param("breachStartedTo") OffsetDateTime breachStartedTo,
      @Param("normalizedFrom") OffsetDateTime normalizedFrom,
      @Param("normalizedTo") OffsetDateTime normalizedTo,
      @Param("minDurationSeconds") Long minDurationSeconds,
      @Param("maxDurationSeconds") Long maxDurationSeconds,
      @Param("metricNamePattern") String metricNamePattern,
      @Param("macAddressPattern") String macAddressPattern,
      @Param("deviceIpPattern") String deviceIpPattern,
      @Param("deviceNamePattern") String deviceNamePattern,
      @Param("deviceIdsCsv") String deviceIdsCsv,
      @Param("deviceTagsCsv") String deviceTagsCsv
  );
}
