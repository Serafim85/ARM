package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.workstation.dto.WorkstationFilter;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class WorkstationSpecifications {

  private WorkstationSpecifications() {
  }

  static Specification<WorkstationEntity> fromFilter(
      WorkstationFilter filter,
      int offlineThresholdMinutes,
      OffsetDateTime now
  ) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter != null) {
        if (filter.q() != null && !filter.q().isBlank()) {
          String pattern = "%" + filter.q().trim().toLowerCase(Locale.ROOT) + "%";
          predicates.add(cb.or(
              cb.like(cb.lower(root.get("hostname")), pattern),
              cb.like(cb.lower(root.get("displayName")), pattern),
              cb.like(cb.lower(root.get("primaryIp")), pattern)
          ));
        }
        if (filter.osType() != null && !filter.osType().isBlank() && !"all".equalsIgnoreCase(filter.osType())) {
          predicates.add(cb.equal(cb.lower(root.get("osType")), filter.osType().trim().toLowerCase(Locale.ROOT)));
        }
        if (filter.status() != null && !filter.status().isBlank() && !"all".equalsIgnoreCase(filter.status())) {
          if (WorkstationStatusSupport.STATUS_ONLINE.equalsIgnoreCase(filter.status())) {
            predicates.add(effectiveOnlinePredicate(root, cb, offlineThresholdMinutes, now));
          } else if (WorkstationStatusSupport.STATUS_OFFLINE.equalsIgnoreCase(filter.status())) {
            predicates.add(effectiveOfflinePredicate(root, cb, offlineThresholdMinutes, now));
          } else {
            predicates.add(cb.equal(cb.lower(root.get("status")), filter.status().trim().toLowerCase(Locale.ROOT)));
          }
        }
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  static Specification<WorkstationEntity> effectiveOnline(int offlineThresholdMinutes, OffsetDateTime now) {
    return (root, query, cb) -> effectiveOnlinePredicate(root, cb, offlineThresholdMinutes, now);
  }

  private static Predicate effectiveOnlinePredicate(
      jakarta.persistence.criteria.Root<WorkstationEntity> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      int offlineThresholdMinutes,
      OffsetDateTime now
  ) {
    OffsetDateTime cutoff = now.minusMinutes(Math.max(offlineThresholdMinutes, 1));
    return cb.and(
        cb.isNotNull(root.get("lastSeenAt")),
        cb.greaterThanOrEqualTo(root.get("lastSeenAt"), cutoff)
    );
  }

  private static Predicate effectiveOfflinePredicate(
      jakarta.persistence.criteria.Root<WorkstationEntity> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      int offlineThresholdMinutes,
      OffsetDateTime now
  ) {
    OffsetDateTime cutoff = now.minusMinutes(Math.max(offlineThresholdMinutes, 1));
    return cb.or(
        cb.isNull(root.get("lastSeenAt")),
        cb.lessThan(root.get("lastSeenAt"), cutoff)
    );
  }
}
