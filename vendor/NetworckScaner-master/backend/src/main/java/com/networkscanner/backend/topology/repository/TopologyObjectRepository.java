package com.networkscanner.backend.topology.repository;

import com.networkscanner.backend.topology.model.AbstractTopologyObject;
import com.networkscanner.backend.topology.model.TopologyEdgeObject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopologyObjectRepository extends JpaRepository<AbstractTopologyObject, Long> {

  List<AbstractTopologyObject> findByTopology_IdOrderById(Long topologyId);

  Optional<AbstractTopologyObject> findByTopology_IdAndElementId(Long topologyId, String elementId);

  @Query(
      "select o from AbstractTopologyObject o join fetch o.topology t where o.id = :id and t.id = :topologyId"
  )
  Optional<AbstractTopologyObject> findByIdAndTopology_Id(@Param("id") Long id, @Param("topologyId") Long topologyId);

  List<AbstractTopologyObject> findByTopology_IdAndLayer_IdOrderById(Long topologyId, Long layerId);

  List<AbstractTopologyObject> findByTopology_IdAndLayerIsNullOrderById(Long topologyId);

  @Query("select e from TopologyEdgeObject e where e.source.id = :oid or e.target.id = :oid")
  List<TopologyEdgeObject> findEdgesIncidentTo(@Param("oid") Long objectId);
}
