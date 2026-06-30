package com.networkscanner.backend.dashboards.impl;

import com.networkscanner.backend.dashboards.model.AbstractWidgetEntity;
import com.networkscanner.backend.dashboards.model.ClockWidgetEntity;
import com.networkscanner.backend.dashboards.model.DashboardEntity;
import com.networkscanner.backend.dashboards.model.GraphWidgetEntity;
import com.networkscanner.backend.dashboards.model.PlaceholderWidgetEntity;
import com.networkscanner.backend.dashboards.model.ProblemsWidgetEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class WidgetSpecifications {

  private WidgetSpecifications() {
  }

  public static Specification<AbstractWidgetEntity> accessible(Long userId, boolean admin) {
    return (root, query, cb) -> {
      Join<AbstractWidgetEntity, DashboardEntity> d = root.join("dashboard", JoinType.INNER);
      if (!Long.class.equals(query.getResultType())) {
        d.fetch("owner", JoinType.INNER);
        query.distinct(true);
      }
      if (admin) {
        return cb.conjunction();
      }
      var ownerMatch = cb.equal(d.get("owner").get("id"), userId);
      Subquery<Integer> sq = query.subquery(Integer.class);
      Root<DashboardEntity> dr = sq.from(DashboardEntity.class);
      SetJoin<DashboardEntity, Long> shared = dr.joinSet("sharedUserIds", JoinType.INNER);
      sq.select(cb.literal(1));
      sq.where(cb.and(cb.equal(dr.get("id"), d.get("id")), cb.equal(shared, userId)));
      return cb.or(ownerMatch, cb.exists(sq));
    };
  }

  public static Specification<AbstractWidgetEntity> nameContains(String search) {
    return (root, query, cb) -> {
      if (search == null || search.isBlank()) {
        return cb.conjunction();
      }
      String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
      return cb.like(cb.lower(root.get("name")), pattern);
    };
  }

  public static Specification<AbstractWidgetEntity> dashboardIdEquals(Long dashboardId) {
    return (root, query, cb) -> {
      if (dashboardId == null) {
        return cb.conjunction();
      }
      Join<AbstractWidgetEntity, DashboardEntity> d = root.join("dashboard", JoinType.INNER);
      return cb.equal(d.get("id"), dashboardId);
    };
  }

  public static Specification<AbstractWidgetEntity> widgetTypeEquals(String widgetType) {
    return (root, query, cb) -> {
      if (widgetType == null || widgetType.isBlank()) {
        return cb.conjunction();
      }
      String t = widgetType.strip();
      if (!"PLACEHOLDER".equalsIgnoreCase(t)
          && !"CLOCK".equalsIgnoreCase(t)
          && !"PROBLEMS".equalsIgnoreCase(t)
          && !"GRAPH".equalsIgnoreCase(t)) {
        throw new IllegalArgumentException(
            "Неизвестный тип виджета. Допустимо: PLACEHOLDER, CLOCK, PROBLEMS, GRAPH.");
      }
      if ("CLOCK".equalsIgnoreCase(t)) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ClockWidgetEntity> pr = sq.from(ClockWidgetEntity.class);
        sq.select(pr.get("id"));
        sq.where(cb.equal(pr.get("id"), root.get("id")));
        return cb.exists(sq);
      }
      if ("PROBLEMS".equalsIgnoreCase(t)) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ProblemsWidgetEntity> pr = sq.from(ProblemsWidgetEntity.class);
        sq.select(pr.get("id"));
        sq.where(cb.equal(pr.get("id"), root.get("id")));
        return cb.exists(sq);
      }
      if ("GRAPH".equalsIgnoreCase(t)) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<GraphWidgetEntity> pr = sq.from(GraphWidgetEntity.class);
        sq.select(pr.get("id"));
        sq.where(cb.equal(pr.get("id"), root.get("id")));
        return cb.exists(sq);
      }
      Subquery<Long> sq = query.subquery(Long.class);
      Root<PlaceholderWidgetEntity> pr = sq.from(PlaceholderWidgetEntity.class);
      sq.select(pr.get("id"));
      sq.where(cb.equal(pr.get("id"), root.get("id")));
      return cb.exists(sq);
    };
  }
}
