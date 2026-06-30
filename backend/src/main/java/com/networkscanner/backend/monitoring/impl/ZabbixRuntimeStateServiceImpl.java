package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZabbixRuntimeStateServiceImpl implements ZabbixRuntimeStateService {

  private static final Logger log = LoggerFactory.getLogger(ZabbixRuntimeStateServiceImpl.class);

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ZabbixRuntimeStateServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<String, List<DiscoveryInstanceRuntime>> loadActiveDiscoveryInstances(MonitoredDeviceEntity device) {
    List<DiscoveryInstanceRuntime> rows = jdbcTemplate.query(
        """
            SELECT discovery_rule_key, instance_key, macros_json, last_discovered_at, expires_at
            FROM monitoring_discovery_instances
            WHERE device_id = ? AND active = TRUE AND expires_at >= ?
            ORDER BY discovery_rule_key, instance_key
            """,
        (rs, rowNum) -> new DiscoveryInstanceRuntime(
            rs.getString("discovery_rule_key"),
            rs.getString("instance_key"),
            readMacros(rs.getString("macros_json")),
            rs.getObject("last_discovered_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class)
        ),
        device.getId(),
        OffsetDateTime.now()
    );
    Map<String, List<DiscoveryInstanceRuntime>> grouped = new LinkedHashMap<>();
    for (DiscoveryInstanceRuntime row : rows) {
      grouped.computeIfAbsent(row.discoveryRuleKey(), ignored -> new ArrayList<>()).add(row);
    }
    return grouped;
  }

  @Override
  @Transactional
  public void replaceDiscoveryInstances(
      MonitoredDeviceEntity device,
      String templateId,
      String discoveryRuleKey,
      List<DiscoveryInstanceRuntime> instances
  ) {
    jdbcTemplate.update(
        """
            UPDATE monitoring_discovery_instances
            SET active = FALSE
            WHERE device_id = ? AND discovery_rule_key = ?
            """,
        device.getId(),
        discoveryRuleKey
    );

    for (DiscoveryInstanceRuntime instance : instances) {
      jdbcTemplate.update(
          """
              INSERT INTO monitoring_discovery_instances (
                device_id, template_id, discovery_rule_key, instance_key, macros_json,
                last_discovered_at, expires_at, active
              )
              VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
              ON CONFLICT (device_id, discovery_rule_key, instance_key) DO UPDATE SET
                template_id = EXCLUDED.template_id,
                macros_json = EXCLUDED.macros_json,
                last_discovered_at = EXCLUDED.last_discovered_at,
                expires_at = EXCLUDED.expires_at,
                active = TRUE
              """,
          device.getId(),
          templateId,
          discoveryRuleKey,
          instance.instanceKey(),
          writeMacros(instance.macros()),
          instance.lastDiscoveredAt(),
          instance.expiresAt()
      );
    }
  }

  @Override
  public Map<String, ItemStateSnapshot> loadItemState(MonitoredDeviceEntity device) {
    List<ItemStateSnapshot> rows = loadItemStateList(device);
    Map<String, ItemStateSnapshot> state = new LinkedHashMap<>();
    for (ItemStateSnapshot row : rows) {
      state.put(stateKey(row.itemKey(), row.instanceKey()), row);
    }
    return state;
  }

  @Override
  public List<ItemStateSnapshot> loadItemStateList(MonitoredDeviceEntity device) {
    return jdbcTemplate.query(
        """
            SELECT template_id, item_key, instance_key, numeric_value, text_value, unit_label, value_map_name,
                   preprocessing_status, preprocessing_note, last_collected_at
            FROM monitoring_item_state
            WHERE device_id = ?
            """,
        (rs, rowNum) -> new ItemStateSnapshot(
            rs.getString("template_id"),
            rs.getString("item_key"),
            rs.getString("instance_key"),
            rs.getObject("numeric_value", Double.class),
            rs.getString("text_value"),
            rs.getString("unit_label"),
            rs.getString("value_map_name"),
            rs.getString("preprocessing_status"),
            rs.getString("preprocessing_note"),
            rs.getObject("last_collected_at", OffsetDateTime.class)
        ),
        device.getId()
    );
  }

  @Override
  @Transactional
  public void saveItemValues(
      MonitoredDeviceEntity device,
      String templateId,
      String templateVersion,
      String packVersion,
      List<ZabbixItemValue> values,
      OffsetDateTime timestamp
  ) {
    List<ZabbixItemValue> numericValues = values.stream()
        .filter(value -> value.numericValue() != null)
        .toList();
    if (!numericValues.isEmpty()) {
      jdbcTemplate.batchUpdate(
          """
              INSERT INTO metric_values (
                recorded_at, device_ip, metric_name, metric_value, item_key, instance_key, unit_label
              )
              VALUES (?, ?, ?, ?, ?, ?, ?)
              """,
          new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
              ZabbixItemValue value = numericValues.get(i);
              ps.setObject(1, timestamp);
              ps.setString(2, device.getIp());
              ps.setString(3, value.metricName());
              ps.setObject(4, value.numericValue());
              ps.setString(5, value.itemKey());
              ps.setString(6, blankToNull(value.instanceKey()));
              ps.setString(7, blankToNull(value.unitLabel()));
            }

            @Override
            public int getBatchSize() {
              return numericValues.size();
            }
          }
      );
    }

    jdbcTemplate.batchUpdate(
        """
            INSERT INTO monitoring_item_state (
              device_id, template_id, item_key, instance_key, item_uuid,
              discovery_rule_key, unit_label, value_map_name,
              numeric_value, text_value, preprocessing_status, preprocessing_note, last_collected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (device_id, item_key, instance_key) DO UPDATE SET
              template_id = EXCLUDED.template_id,
              item_uuid = EXCLUDED.item_uuid,
              discovery_rule_key = EXCLUDED.discovery_rule_key,
              unit_label = EXCLUDED.unit_label,
              value_map_name = EXCLUDED.value_map_name,
              numeric_value = EXCLUDED.numeric_value,
              text_value = EXCLUDED.text_value,
              preprocessing_status = EXCLUDED.preprocessing_status,
              preprocessing_note = EXCLUDED.preprocessing_note,
              last_collected_at = EXCLUDED.last_collected_at
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            ZabbixItemValue value = values.get(i);
            ps.setLong(1, device.getId());
            ps.setString(2, firstNonBlank(value.templateId(), templateId));
            ps.setString(3, value.itemKey());
            ps.setString(4, normalizeInstanceKey(value.instanceKey()));
            ps.setString(5, value.itemUuid());
            ps.setString(6, blankToNull(value.discoveryRuleKey()));
            ps.setString(7, blankToNull(value.unitLabel()));
            ps.setString(8, blankToNull(value.valueMapName()));
            ps.setObject(9, value.numericValue());
            ps.setString(10, value.textValue());
            ps.setString(11, blankToNull(value.preprocessingStatus()));
            ps.setString(12, blankToNull(value.preprocessingNote()));
            ps.setObject(13, timestamp);
          }

          @Override
          public int getBatchSize() {
            return values.size();
          }
        }
    );
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  @Override
  public List<Double> loadRecentNumericValues(
      MonitoredDeviceEntity device,
      String metricName,
      String instanceKey,
      OffsetDateTime since,
      Integer limit
  ) {
    StringBuilder sql = new StringBuilder(
        "SELECT metric_value FROM metric_values WHERE device_ip = ? AND metric_name = ?"
    );
    List<Object> params = new ArrayList<>();
    params.add(device.getIp());
    params.add(metricName);
    if (instanceKey == null || instanceKey.isBlank()) {
      sql.append(" AND (instance_key IS NULL OR instance_key = '')");
    } else {
      sql.append(" AND instance_key = ?");
      params.add(instanceKey);
    }
    if (since != null) {
      sql.append(" AND recorded_at >= ?");
      params.add(since);
    }
    sql.append(" ORDER BY recorded_at DESC");
    if (limit != null && limit > 0) {
      sql.append(" LIMIT ").append(limit);
    }
    return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getDouble("metric_value"), params.toArray());
  }

  @Override
  public Map<MetricHistoryRequest, List<MetricHistoryPoint>> loadMetricHistoryBatch(
      MonitoredDeviceEntity device,
      List<MetricHistoryRequest> requests
  ) {
    if (requests == null || requests.isEmpty()) {
      return Map.of();
    }

    List<MetricHistoryRequest> uniqueRequests = requests.stream().distinct().toList();
    Map<MetricHistoryRequest, List<MetricHistoryPoint>> result = new LinkedHashMap<>();
    for (MetricHistoryRequest request : uniqueRequests) {
      result.put(request, new ArrayList<>());
    }

    Map<HistoryWindowKey, List<MetricHistoryRequest>> groupedRequests = uniqueRequests.stream()
        .collect(Collectors.groupingBy(
            request -> new HistoryWindowKey(request.since(), request.limit()),
            LinkedHashMap::new,
            Collectors.toList()
        ));

    for (Map.Entry<HistoryWindowKey, List<MetricHistoryRequest>> entry : groupedRequests.entrySet()) {
      HistoryWindowKey window = entry.getKey();
      List<MetricHistoryRequest> groupRequests = entry.getValue();
      Map<String, MetricHistoryRequest> requestIndex = groupRequests.stream()
          .collect(Collectors.toMap(
              MetricHistoryRequest::metricName,
              request -> request,
              (left, right) -> left,
              LinkedHashMap::new
          ));

      List<Object> params = new ArrayList<>();
      StringBuilder sql = new StringBuilder();
      if (window.limit() != null && window.limit() > 0) {
        sql.append("""
            SELECT metric_name, recorded_at, metric_value
            FROM (
              SELECT metric_name, recorded_at, metric_value,
                     ROW_NUMBER() OVER (PARTITION BY metric_name ORDER BY recorded_at DESC) AS rn
              FROM metric_values
              WHERE device_ip = ?
            """);
      } else {
        sql.append("""
            SELECT metric_name, recorded_at, metric_value
            FROM metric_values
            WHERE device_ip = ?
            """);
      }
      params.add(device.getIp());
      sql.append(" AND metric_name IN (").append(placeholders(groupRequests.size())).append(")");
      for (MetricHistoryRequest request : groupRequests) {
        params.add(request.metricName());
      }
      if (window.since() != null) {
        sql.append(" AND recorded_at >= ?");
        params.add(window.since());
      }
      if (window.limit() != null && window.limit() > 0) {
        sql.append(" ) ranked WHERE rn <= ?");
        params.add(window.limit());
      }
      sql.append(" ORDER BY metric_name, recorded_at DESC");

      jdbcTemplate.query(sql.toString(), rs -> {
        MetricHistoryRequest request = requestIndex.get(rs.getString("metric_name"));
        if (request != null) {
          result.computeIfAbsent(request, ignored -> new ArrayList<>())
              .add(new MetricHistoryPoint(
                  rs.getObject("recorded_at", OffsetDateTime.class),
                  rs.getDouble("metric_value")
              ));
        }
      }, params.toArray());
    }

    return result;
  }

  @Override
  @Transactional
  public void purgeDeviceHistory(Collection<String> deviceIps) {
    if (deviceIps == null || deviceIps.isEmpty()) {
      return;
    }
    List<String> ips = deviceIps.stream()
        .filter(ip -> ip != null && !ip.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    if (ips.isEmpty()) {
      return;
    }
    Object[] params = ips.toArray();
    String inClause = placeholders(ips.size());
    int metricRows = jdbcTemplate.update(
        "DELETE FROM metric_values WHERE device_ip IN (" + inClause + ")",
        params
    );
    int availabilityRows = jdbcTemplate.update(
        "DELETE FROM availability_history WHERE device_ip IN (" + inClause + ")",
        params
    );
    int telemetryRows = jdbcTemplate.update(
        "DELETE FROM telemetry_history WHERE device_ip IN (" + inClause + ")",
        params
    );
    int aggregateRows = purgeMetricValuesAggregate(inClause, params);
    log.info(
        "Purged device history for {} IP(s): metric_values={}, metric_values_1h={},"
            + " availability_history={}, telemetry_history={}",
        ips.size(),
        metricRows,
        aggregateRows,
        availabilityRows,
        telemetryRows
    );
  }

  private int purgeMetricValuesAggregate(String inClause, Object[] params) {
    if (!aggregateViewExists()) {
      return 0;
    }
    return jdbcTemplate.update(
        "DELETE FROM metric_values_1h WHERE device_ip IN (" + inClause + ")",
        params
    );
  }

  private boolean aggregateViewExists() {
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relname = 'metric_values_1h'
            )
            """,
        Boolean.class
    );
    return Boolean.TRUE.equals(exists);
  }

  @Override
  @Transactional
  public void removeItemState(Long deviceId, String itemUuid, String instanceKey) {
    String normalizedInstance = normalizeInstanceKey(instanceKey);
    if ("*".equals(normalizedInstance)) {
      jdbcTemplate.update(
          """
              DELETE FROM monitoring_item_state
              WHERE device_id = ? AND item_uuid = ?
              """,
          deviceId,
          itemUuid
      );
      return;
    }
    jdbcTemplate.update(
        """
            DELETE FROM monitoring_item_state
            WHERE device_id = ? AND item_uuid = ? AND instance_key = ?
            """,
        deviceId,
        itemUuid,
        normalizedInstance
    );
  }

  private Map<String, String> readMacros(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {
      });
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось прочитать discovery macros.", exception);
    }
  }

  private String writeMacros(Map<String, String> macros) {
    try {
      return objectMapper.writeValueAsString(macros == null ? Map.of() : macros);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать discovery macros.", exception);
    }
  }

  private String stateKey(String itemKey, String instanceKey) {
    return itemKey + "::" + normalizeInstanceKey(instanceKey);
  }

  private String normalizeInstanceKey(String instanceKey) {
    return instanceKey == null ? "" : instanceKey;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String placeholders(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(ignored -> "?")
        .collect(Collectors.joining(", "));
  }

  private record HistoryWindowKey(
      OffsetDateTime since,
      Integer limit
  ) {
  }
}
