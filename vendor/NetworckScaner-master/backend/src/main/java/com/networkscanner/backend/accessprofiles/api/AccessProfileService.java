package com.networkscanner.backend.accessprofiles.api;

import com.networkscanner.backend.accessprofiles.dto.AccessProfileDetailDto;
import com.networkscanner.backend.accessprofiles.dto.AccessProfileSummaryDto;
import com.networkscanner.backend.accessprofiles.dto.UpsertAccessProfileRequest;
import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import java.util.List;

public interface AccessProfileService {

  List<AccessProfileSummaryDto> listSummaries();

  List<AccessProfileDetailDto> listDetails();

  AccessProfileDetailDto getById(Long id);

  AccessProfileEntity requireEntity(Long id);

  AccessProfileDetailDto create(UpsertAccessProfileRequest request);

  AccessProfileDetailDto update(Long id, UpsertAccessProfileRequest request);

  void delete(Long id);
}
