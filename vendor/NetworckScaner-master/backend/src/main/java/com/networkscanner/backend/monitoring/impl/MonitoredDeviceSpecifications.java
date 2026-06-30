package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.MonitoringHostAvailabilityFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostFilter;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class MonitoredDeviceSpecifications {

  private static final String AVAILABLE_STATUS = "Включено";
  private static final String UNAVAILABLE_STATUS = "Недоступно";

  private MonitoredDeviceSpecifications() {
  }

  static Specification<MonitoredDeviceEntity> fromFilter(MonitoringHostFilter filter) {
    return Specification.where(textContains("ip", filter.ip()))
        .and(textContains("macAddress", filter.macAddress()))
        .and(textContains("pollingStatus", filter.pollingStatus()))
        .and(tagsContain(filter.tag()))
        .and(healthStatusEquals(filter))
        .and(searchQuery(filter.q()))
        .and(hostAvailability(filter.hostAvailability()));
  }

  static Specification<MonitoredDeviceEntity> withoutAvailability(MonitoringHostFilter filter) {
    return Specification.where(textContains("ip", filter.ip()))
        .and(textContains("macAddress", filter.macAddress()))
        .and(textContains("pollingStatus", filter.pollingStatus()))
        .and(tagsContain(filter.tag()))
        .and(healthStatusEquals(filter))
        .and(searchQuery(filter.q()));
  }

  static Specification<MonitoredDeviceEntity> hostAvailability(MonitoringHostAvailabilityFilter availability) {
    if (availability == null) {
      return null;
    }
    return (root, query, cb) -> switch (availability) {
      case AVAILABLE -> cb.equal(root.get("status"), AVAILABLE_STATUS);
      case UNAVAILABLE -> cb.equal(root.get("status"), UNAVAILABLE_STATUS);
      case UNKNOWN -> cb.and(
          cb.notEqual(root.get("status"), AVAILABLE_STATUS),
          cb.notEqual(root.get("status"), UNAVAILABLE_STATUS)
      );
    };
  }

  private static Specification<MonitoredDeviceEntity> healthStatusEquals(MonitoringHostFilter filter) {
    if (filter.healthStatus() == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("healthStatus"), filter.healthStatus());
  }

  private static Specification<MonitoredDeviceEntity> searchQuery(String query) {
    String normalized = normalizeLikeValue(query);
    if (normalized == null) {
      return null;
    }
    String pattern = "%" + normalized + "%";
    return (root, q, cb) -> {
      List<Predicate> fields = new ArrayList<>();
      fields.add(cb.like(cb.lower(root.get("hostName")), pattern));
      fields.add(cb.like(cb.lower(root.get("name")), pattern));
      fields.add(cb.like(cb.lower(root.get("ip")), pattern));
      fields.add(cb.like(cb.lower(root.get("macAddress")), pattern));
      return cb.or(fields.toArray(new Predicate[0]));
    };
  }

  private static Specification<MonitoredDeviceEntity> tagsContain(String tagCsv) {
    String normalized = normalizeLikeValue(tagCsv);
    if (normalized == null) {
      return null;
    }
    String[] parts = normalized.split(",");
    List<String> tags = new ArrayList<>();
    for (String p : parts) {
      String t = normalizeLikeValue(p);
      if (t != null) {
        tags.add(t);
      }
    }
    if (tags.isEmpty()) {
      return null;
    }
    return (root, query, cb) -> {
      List<Predicate> likes = new ArrayList<>();
      for (String t : tags) {
        // tagsJson is stored as JSON array string; match loosely to avoid dependency on formatting/quoting.
        String escaped = t
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        likes.add(cb.like(cb.lower(root.get("tagsJson")), pattern));
      }
      return cb.or(likes.toArray(new Predicate[0]));
    };
  }

  private static Specification<MonitoredDeviceEntity> textContains(String field, String value) {
    String normalized = normalizeLikeValue(value);
    if (normalized == null) {
      return null;
    }
    String pattern = "%" + normalized + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
  }

  private static String normalizeLikeValue(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() ? null : normalized;
  }
}
