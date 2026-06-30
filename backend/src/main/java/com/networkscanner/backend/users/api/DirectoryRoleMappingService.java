package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.DirectoryGroupDto;
import com.networkscanner.backend.users.dto.DirectoryRoleMappingDto;
import com.networkscanner.backend.users.dto.UpdateDirectoryRoleMappingsRequest;
import com.networkscanner.backend.users.model.RoleName;
import java.util.List;
import java.util.Set;

public interface DirectoryRoleMappingService {

  List<DirectoryRoleMappingDto> listMappings();

  List<DirectoryRoleMappingDto> updateMappings(UpdateDirectoryRoleMappingsRequest request);

  List<DirectoryGroupDto> discoverGroups();

  Set<RoleName> resolveRolesForGroups(List<String> groupDns);
}
