package com.networkscanner.backend.topology.repository;

import com.networkscanner.backend.topology.model.TopologyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopologyRepository extends JpaRepository<TopologyEntity, Long> {

  List<TopologyEntity> findByOwner_IdOrderByUpdatedAtDesc(Long ownerId);

  @Query(
      "select distinct t from TopologyEntity t where t.owner.id = :userId "
          + "or :userId member of t.sharedUserIds")
  List<TopologyEntity> findAllAccessibleByUserId(@Param("userId") Long userId);

  @Query("select t from TopologyEntity t join fetch t.owner where t.id = :id")
  Optional<TopologyEntity> findFetchedById(@Param("id") Long id);
}
