package com.networkscanner.backend.topology.api;

import com.networkscanner.backend.topology.dto.TopologyLayerBackgroundBytes;
import com.networkscanner.backend.topology.dto.TopologyLayoutBatchUpdateRequest;
import com.networkscanner.backend.topology.dto.TopologyObjectCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyObjectDto;
import com.networkscanner.backend.topology.dto.TopologyObjectUpdateRequest;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface TopologyObjectService {

  List<TopologyObjectDto> listByTopology(Long topologyId, Long layerId, Authentication authentication);

  TopologyObjectDto getById(Long topologyId, Long objectId, Authentication authentication);

  TopologyObjectDto create(Long topologyId, TopologyObjectCreateRequest request, Authentication authentication);

  TopologyObjectDto update(
      Long topologyId,
      Long objectId,
      TopologyObjectUpdateRequest request,
      Authentication authentication
  );

  /**
   * Атомарно применить набор правок координат/размеров рамок (одна транзакция БД).
   */
  void applyLayoutBatch(
      Long topologyId,
      TopologyLayoutBatchUpdateRequest request,
      Authentication authentication
  );

  void delete(Long topologyId, Long objectId, Authentication authentication);

  TopologyObjectDto uploadLayerBackground(
      Long topologyId,
      Long objectId,
      byte[] bytes,
      String declaredContentType,
      Authentication authentication
  );

  void deleteLayerBackground(Long topologyId, Long objectId, Authentication authentication);

  TopologyLayerBackgroundBytes getLayerBackground(Long topologyId, Long objectId, Authentication authentication);
}
