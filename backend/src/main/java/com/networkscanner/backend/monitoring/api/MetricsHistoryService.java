package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.lang.Nullable;

public interface MetricsHistoryService {

  void recordAvailability(DeviceScanResult device);

  void recordTelemetry(DeviceScanResult device);

  List<MetricValueDto> queryMetricValues(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      @Nullable String metricName
  );

  /**
   * Чтение точек только для заданного набора метрик с опциональной децимацией под отображение.
   *
   * @param metricNames фильтр по именам метрик; {@code null}/пусто — все метрики устройства
   * @param maxPoints   максимум точек на ряд; {@code null}/&le;0 — без децимации (сырые точки)
   */
  List<MetricValueDto> queryMetricValues(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      @Nullable Collection<String> metricNames,
      @Nullable Integer maxPoints
  );

  /**
   * Имена метрик, у которых есть данные в интервале (по выбранному тиру raw/hourly/hybrid).
   * Лёгкий запрос для построения раскладки панелей без чтения самих точек.
   */
  List<String> listMetricNamesInRange(
      String deviceIp,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to
  );

  /**
   * Последняя сохранённая точка по каждой метрике для устройства (по {@code metric_name}).
   */
  List<MetricValueDto> queryLatestMetricValues(String deviceIp, @Nullable String metricName);
}
