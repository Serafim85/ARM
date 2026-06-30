package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.MetricsHistoryService;
import com.networkscanner.backend.monitoring.dto.AvailabilityDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class MetricsHistoryServiceImpl implements MetricsHistoryService {

  private static final Logger log = LoggerFactory.getLogger(MetricsHistoryServiceImpl.class);

  /** Chart queries use raw points for data newer than this age. */
  static final int CHART_RAW_MAX_AGE_DAYS = 7;

  private static final String HOURLY_AGGREGATE_VIEW = "metric_values_1h";

  private final JdbcTemplate jdbcTemplate;
  private volatile Boolean hourlyAggregateViewAvailable;

  public MetricsHistoryServiceImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void recordAvailability(DeviceScanResult device) {
    boolean icmp = isProtocolActive(device.availability(), "ICMP");
    boolean snmp = isProtocolActive(device.availability(), "SNMP");
    boolean ssh = isProtocolActive(device.availability(), "SSH");

    jdbcTemplate.update(
        """
            INSERT INTO availability_history (recorded_at, device_ip, host_status, icmp_active, snmp_active, ssh_active)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        OffsetDateTime.now(),
        device.ip(),
        device.status(),
        icmp,
        snmp,
        ssh
    );
  }

  @Override
  public void recordTelemetry(DeviceScanResult device) {
    int seed = device.ip().chars().sum();
    jdbcTemplate.update(
        """
            INSERT INTO telemetry_history (recorded_at, device_ip, cpu_usage, ram_usage, rom_usage)
            VALUES (?, ?, ?, ?, ?)
            """,
        OffsetDateTime.now(),
        device.ip(),
        percentage(8 + (seed % 21)),
        percentage(35 + (seed % 40)),
        percentage(20 + (seed % 35))
    );
  }

  @Override
  public List<MetricValueDto> queryMetricValues(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      @Nullable String metricName
  ) {
    Collection<String> names = (metricName == null || metricName.isBlank())
        ? List.of()
        : List.of(metricName);
    return queryMetricValues(deviceIp, from, to, names, null);
  }

  @Override
  public List<MetricValueDto> queryMetricValues(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      @Nullable Collection<String> metricNames,
      @Nullable Integer maxPoints
  ) {
    List<String> names = normalizeMetricNames(metricNames);
    HistoryTier tier = effectiveTier(from, to);

    SqlWithParams inner = switch (tier) {
      case RAW -> rawInnerQuery(deviceIp, from, to, names);
      case HOURLY -> hourlyInnerQuery(deviceIp, from, to, names);
      case HYBRID -> hybridInnerQuery(deviceIp, from, to, chartRawCutoff(), names);
    };

    Long bucketSeconds = decimationBucketSeconds(from, to, maxPoints);
    if (bucketSeconds == null) {
      String sql = inner.sql() + " ORDER BY recorded_at DESC LIMIT 1000000";
      return mapMetricRows(sql, inner.params());
    }
    return mapMetricRows(wrapWithDecimation(inner), decimationParams(inner, bucketSeconds));
  }

  @Override
  public List<String> listMetricNamesInRange(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to
  ) {
    HistoryTier tier = effectiveTier(from, to);
    List<Object> params = new ArrayList<>();
    String sql = switch (tier) {
      case RAW -> {
        StringBuilder b = new StringBuilder(
            "SELECT DISTINCT metric_name FROM metric_values WHERE device_ip = ? AND metric_value IS NOT NULL"
        );
        params.add(deviceIp);
        appendRange(b, params, "recorded_at", from, to);
        yield b.toString();
      }
      case HOURLY -> {
        StringBuilder b = new StringBuilder(
            "SELECT DISTINCT metric_name FROM metric_values_1h WHERE device_ip = ? AND sample_count > 0"
        );
        params.add(deviceIp);
        appendRange(b, params, "bucket", from, to);
        yield b.toString();
      }
      case HYBRID -> {
        OffsetDateTime cutoff = chartRawCutoff();
        StringBuilder b = new StringBuilder(
            "SELECT DISTINCT metric_name FROM metric_values_1h WHERE device_ip = ? AND sample_count > 0"
        );
        params.add(deviceIp);
        b.append(" AND bucket >= ? AND bucket < ?");
        params.add(from);
        params.add(cutoff);
        b.append(" UNION SELECT DISTINCT metric_name FROM metric_values "
            + "WHERE device_ip = ? AND metric_value IS NOT NULL AND recorded_at >= ? AND recorded_at <= ?");
        params.add(deviceIp);
        params.add(cutoff);
        params.add(to);
        yield b.toString();
      }
    };
    return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> rs.getString("metric_name"))
        .stream()
        .filter(name -> name != null && !name.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private HistoryTier effectiveTier(@Nullable OffsetDateTime from, @Nullable OffsetDateTime to) {
    HistoryTier tier = resolveHistoryTier(from, to, OffsetDateTime.now());
    if (tier != HistoryTier.RAW && !isHourlyAggregateViewAvailable()) {
      return HistoryTier.RAW;
    }
    return tier;
  }

  private static List<String> normalizeMetricNames(@Nullable Collection<String> metricNames) {
    if (metricNames == null || metricNames.isEmpty()) {
      return List.of();
    }
    return metricNames.stream()
        .filter(name -> name != null && !name.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  /** Размер бакета децимации в секундах или {@code null}, если децимация не нужна. */
  private static Long decimationBucketSeconds(
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      @Nullable Integer maxPoints
  ) {
    if (maxPoints == null || maxPoints <= 0 || from == null || to == null) {
      return null;
    }
    long spanSeconds = Duration.between(from, to).getSeconds();
    if (spanSeconds <= 0) {
      return null;
    }
    long bucket = spanSeconds / maxPoints;
    return bucket < 1 ? null : bucket;
  }

  private static String wrapWithDecimation(SqlWithParams inner) {
    return "SELECT time_bucket(make_interval(secs => ?), base.recorded_at) AS recorded_at, "
        + "base.device_ip, base.metric_name, avg(base.metric_value) AS metric_value, "
        + "max(base.unit_label) AS unit_label "
        + "FROM (" + inner.sql() + ") base "
        + "GROUP BY 1, base.device_ip, base.metric_name "
        + "ORDER BY recorded_at DESC";
  }

  private static List<Object> decimationParams(SqlWithParams inner, long bucketSeconds) {
    List<Object> params = new ArrayList<>();
    params.add((double) bucketSeconds);
    params.addAll(inner.params());
    return params;
  }

  private record SqlWithParams(String sql, List<Object> params) {
  }

  enum HistoryTier {
    RAW,
    HOURLY,
    HYBRID
  }

  static HistoryTier resolveHistoryTier(
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      OffsetDateTime now
  ) {
    if (from == null || to == null) {
      return HistoryTier.RAW;
    }
    OffsetDateTime cutoff = chartRawCutoff(now);
    if (!to.isAfter(cutoff)) {
      return HistoryTier.HOURLY;
    }
    if (!from.isBefore(cutoff)) {
      return HistoryTier.RAW;
    }
    return HistoryTier.HYBRID;
  }

  private SqlWithParams rawInnerQuery(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      List<String> metricNames
  ) {
    StringBuilder sql = new StringBuilder(
        "SELECT recorded_at, device_ip, metric_name, metric_value, unit_label "
            + "FROM metric_values WHERE device_ip = ? AND metric_value IS NOT NULL"
    );
    List<Object> params = new ArrayList<>();
    params.add(deviceIp);
    appendRange(sql, params, "recorded_at", from, to);
    appendMetricNamesFilter(sql, params, metricNames);
    return new SqlWithParams(sql.toString(), params);
  }

  private SqlWithParams hourlyInnerQuery(
      String deviceIp,
      OffsetDateTime from,
      OffsetDateTime to,
      List<String> metricNames
  ) {
    StringBuilder sql = new StringBuilder(
        "SELECT bucket AS recorded_at, device_ip, metric_name, avg_value AS metric_value, "
            + "NULL::varchar AS unit_label FROM metric_values_1h WHERE device_ip = ? AND sample_count > 0"
    );
    List<Object> params = new ArrayList<>();
    params.add(deviceIp);
    sql.append(" AND bucket >= ? AND bucket <= ?");
    params.add(from);
    params.add(to);
    appendMetricNamesFilter(sql, params, metricNames);
    log.debug("Querying {} for device {} between {} and {}", HOURLY_AGGREGATE_VIEW, deviceIp, from, to);
    return new SqlWithParams(sql.toString(), params);
  }

  private SqlWithParams hybridInnerQuery(
      String deviceIp,
      OffsetDateTime from,
      OffsetDateTime to,
      OffsetDateTime cutoff,
      List<String> metricNames
  ) {
    StringBuilder sql = new StringBuilder(
        "SELECT bucket AS recorded_at, device_ip, metric_name, avg_value AS metric_value, "
            + "NULL::varchar AS unit_label FROM metric_values_1h "
            + "WHERE device_ip = ? AND sample_count > 0 AND bucket >= ? AND bucket < ?"
    );
    List<Object> params = new ArrayList<>();
    params.add(deviceIp);
    params.add(from);
    params.add(cutoff);
    appendMetricNamesFilter(sql, params, metricNames);
    sql.append(" UNION ALL SELECT recorded_at, device_ip, metric_name, metric_value, unit_label "
        + "FROM metric_values WHERE device_ip = ? AND metric_value IS NOT NULL "
        + "AND recorded_at >= ? AND recorded_at <= ?");
    params.add(deviceIp);
    params.add(cutoff);
    params.add(to);
    appendMetricNamesFilter(sql, params, metricNames);
    log.debug(
        "Querying hybrid {} + raw for device {} between {} and {} (cutoff {})",
        HOURLY_AGGREGATE_VIEW,
        deviceIp,
        from,
        to,
        cutoff
    );
    return new SqlWithParams(sql.toString(), params);
  }

  private static void appendRange(
      StringBuilder sql,
      List<Object> params,
      String column,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to
  ) {
    if (from != null) {
      sql.append(" AND ").append(column).append(" >= ?");
      params.add(from);
    }
    if (to != null) {
      sql.append(" AND ").append(column).append(" <= ?");
      params.add(to);
    }
  }

  private static void appendMetricNamesFilter(
      StringBuilder sql,
      List<Object> params,
      List<String> metricNames
  ) {
    if (metricNames == null || metricNames.isEmpty()) {
      return;
    }
    if (metricNames.size() == 1) {
      sql.append(" AND metric_name = ?");
      params.add(metricNames.get(0));
      return;
    }
    sql.append(" AND metric_name IN (");
    for (int i = 0; i < metricNames.size(); i++) {
      sql.append(i == 0 ? "?" : ", ?");
      params.add(metricNames.get(i));
    }
    sql.append(")");
  }

  private List<MetricValueDto> mapMetricRows(String sql, List<Object> params) {
    return jdbcTemplate.query(
        sql,
        params.toArray(),
        (rs, rowNum) -> new MetricValueDto(
            rs.getObject("recorded_at", OffsetDateTime.class),
            rs.getString("device_ip"),
            rs.getString("metric_name"),
            rs.getDouble("metric_value"),
            rs.getString("unit_label"),
            null
        )
    );
  }

  static OffsetDateTime chartRawCutoff(OffsetDateTime now) {
    return now.minusDays(CHART_RAW_MAX_AGE_DAYS);
  }

  private OffsetDateTime chartRawCutoff() {
    return chartRawCutoff(OffsetDateTime.now());
  }

  private boolean isHourlyAggregateViewAvailable() {
    Boolean cached = hourlyAggregateViewAvailable;
    if (cached != null) {
      return cached;
    }
    Boolean exists = jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relname = ?
            )
            """,
        Boolean.class,
        HOURLY_AGGREGATE_VIEW
    );
    boolean available = Boolean.TRUE.equals(exists);
    hourlyAggregateViewAvailable = available;
    return available;
  }

  @Override
  public List<MetricValueDto> queryLatestMetricValues(String deviceIp, @Nullable String metricName) {
    StringBuilder sql = new StringBuilder(
        """
            SELECT DISTINCT ON (metric_name)
              recorded_at, device_ip, metric_name, metric_value, unit_label
            FROM metric_values
            WHERE device_ip = ? AND metric_value IS NOT NULL
            """
    );
    List<Object> params = new ArrayList<>();
    params.add(deviceIp);
    if (metricName != null && !metricName.isBlank()) {
      sql.append(" AND metric_name = ?");
      params.add(metricName);
    }
    sql.append(" ORDER BY metric_name, recorded_at DESC");

    return jdbcTemplate.query(
        sql.toString(),
        params.toArray(),
        (rs, rowNum) -> new MetricValueDto(
            rs.getObject("recorded_at", OffsetDateTime.class),
            rs.getString("device_ip"),
            rs.getString("metric_name"),
            rs.getDouble("metric_value"),
            rs.getString("unit_label"),
            null
        )
    );
  }

  private boolean isProtocolActive(List<AvailabilityDto> availability, String protocol) {
    return availability.stream()
        .anyMatch(item -> protocol.equalsIgnoreCase(item.label()) && item.active());
  }

  private BigDecimal percentage(int value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
