package com.networkscanner.backend.audit.repository;

import com.networkscanner.backend.audit.dto.AuditEventListQuery;
import com.networkscanner.backend.audit.model.AppAuditEventEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class AppAuditEventSpecifications {

  private AppAuditEventSpecifications() {
  }

  public static Specification<AppAuditEventEntity> matching(AuditEventListQuery query) {
    return (root, cq, cb) -> {
      List<Predicate> preds = new ArrayList<>();
      if (query.occurredFrom() != null) {
        preds.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.occurredFrom()));
      }
      if (query.occurredTo() != null) {
        preds.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.occurredTo()));
      }
      if (query.actorContains() != null && !query.actorContains().isBlank()) {
        String term = escapeLike(query.actorContains().trim()).toLowerCase();
        preds.add(cb.like(cb.lower(root.get("actorLogin")), "%" + term + "%", '\\'));
      }
      if (query.category() != null) {
        preds.add(cb.equal(root.get("category"), query.category()));
      }
      if (query.action() != null) {
        preds.add(cb.equal(root.get("action"), query.action()));
      }
      if (preds.isEmpty()) {
        return cb.conjunction();
      }
      return cb.and(preds.toArray(Predicate[]::new));
    };
  }

  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
