package com.networkscanner.backend.users.repository;

import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectorySettingsRepository extends JpaRepository<DirectorySettingsEntity, Long> {
}
