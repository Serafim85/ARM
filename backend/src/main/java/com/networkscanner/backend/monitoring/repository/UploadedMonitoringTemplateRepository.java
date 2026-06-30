package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedMonitoringTemplateRepository extends JpaRepository<UploadedMonitoringTemplateEntity, Long> {

  Optional<UploadedMonitoringTemplateEntity> findByTemplateId(String templateId);

  List<UploadedMonitoringTemplateEntity> findAllByOrderByTemplateIdAsc();

  long countByExtendsTemplate(String extendsTemplate);
}
