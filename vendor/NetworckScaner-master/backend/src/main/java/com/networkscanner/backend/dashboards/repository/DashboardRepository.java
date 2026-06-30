package com.networkscanner.backend.dashboards.repository;

import com.networkscanner.backend.dashboards.model.DashboardEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends JpaRepository<DashboardEntity, Long> {

  List<DashboardEntity> findByOwner_IdOrderByUpdatedAtDesc(Long ownerId);

  @Query(
      "select distinct d from DashboardEntity d where d.owner.id = :userId "
          + "or :userId member of d.sharedUserIds")
  List<DashboardEntity> findAllAccessibleByUserId(@Param("userId") Long userId);

  @Query(
      "select distinct d from DashboardEntity d left join fetch d.widgets left join fetch d.owner "
          + "where d.id = :id")
  Optional<DashboardEntity> findFetchedById(@Param("id") Long id);
}