package com.networkscanner.backend.accessprofiles.repository;

import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessProfileRepository extends JpaRepository<AccessProfileEntity, Long> {

  List<AccessProfileEntity> findAllByOrderByNameAsc();

  Optional<AccessProfileEntity> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

  @Query(
      value = """
          SELECT COUNT(*) FROM scan_jobs
          WHERE (request_json::jsonb -> 'scan' ->> 'accessProfileId')::bigint = :profileId
          """,
      nativeQuery = true
  )
  long countScanJobsUsingProfile(@Param("profileId") Long profileId);
}
