package com.networkscanner.backend.topology.api;

import com.networkscanner.backend.topology.dto.TopologyCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyDto;
import com.networkscanner.backend.topology.dto.TopologyUpdateRequest;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface TopologyService {

  List<TopologyDto> listAccessible(Authentication authentication);

  TopologyDto create(TopologyCreateRequest request, Authentication authentication);

  TopologyDto update(Long id, TopologyUpdateRequest request, Authentication authentication);

  void delete(Long id, Authentication authentication);

  TopologyDto getById(Long id, Authentication authentication);

  boolean isReadableByUser(Long topologyId, Long userId, boolean admin);
}
