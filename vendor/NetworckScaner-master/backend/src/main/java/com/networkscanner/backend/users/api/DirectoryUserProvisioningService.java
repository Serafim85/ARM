package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.CreateUserFromDirectoryRequest;
import com.networkscanner.backend.users.dto.DirectoryUserCandidateDto;
import com.networkscanner.backend.users.dto.DirectoryUserSearchRequest;
import com.networkscanner.backend.users.dto.UserManagementDto;
import java.util.List;

public interface DirectoryUserProvisioningService {

  List<DirectoryUserCandidateDto> searchUsers(DirectoryUserSearchRequest request);

  UserManagementDto createUserFromDirectory(CreateUserFromDirectoryRequest request);
}
