package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.api.WislaBootstrapService;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.integration.dto.WislaBootstrapRequest;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WislaBootstrapServiceImpl implements WislaBootstrapService {

  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final ProbeBootstrapPayloadMapper payloadMapper;
  private final SourceSystemProvider sourceSystemProvider;

  public WislaBootstrapServiceImpl(
      MonitoredDeviceRepository monitoredDeviceRepository,
      ProbeBootstrapPayloadMapper payloadMapper,
      SourceSystemProvider sourceSystemProvider
  ) {
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.payloadMapper = payloadMapper;
    this.sourceSystemProvider = sourceSystemProvider;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ProbeBootstrapPayload> listMonitoredDevices(WislaBootstrapRequest request) {
    Pageable pageable = PageRequest.of(
        request.page(),
        request.size(),
        Sort.by(Sort.Direction.ASC, "id")
    );
    Page<MonitoredDeviceEntity> page = monitoredDeviceRepository.findAll(
        buildSpecification(request),
        pageable
    );
    String sourceSystem = sourceSystemProvider.getSourceSystem();
    return page.map(entity -> payloadMapper.map(entity, sourceSystem));
  }

  private Specification<MonitoredDeviceEntity> buildSpecification(WislaBootstrapRequest request) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (request.updatedSince() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), request.updatedSince()));
      }
      return predicates.isEmpty()
          ? cb.conjunction()
          : cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
