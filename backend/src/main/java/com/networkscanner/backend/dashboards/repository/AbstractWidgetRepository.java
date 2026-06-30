package com.networkscanner.backend.dashboards.repository;

import com.networkscanner.backend.dashboards.model.AbstractWidgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AbstractWidgetRepository
    extends JpaRepository<AbstractWidgetEntity, Long>, JpaSpecificationExecutor<AbstractWidgetEntity> {
}
