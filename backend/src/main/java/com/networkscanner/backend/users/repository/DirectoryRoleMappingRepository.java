package com.networkscanner.backend.users.repository;

import com.networkscanner.backend.users.model.DirectoryRoleMappingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryRoleMappingRepository extends JpaRepository<DirectoryRoleMappingEntity, Long> {

  List<DirectoryRoleMappingEntity> findAllByOrderByGroupNameAsc();
}
