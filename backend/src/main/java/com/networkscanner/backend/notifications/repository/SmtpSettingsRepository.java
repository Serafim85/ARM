package com.networkscanner.backend.notifications.repository;

import com.networkscanner.backend.notifications.model.SmtpSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpSettingsRepository extends JpaRepository<SmtpSettingsEntity, Long> {
}
