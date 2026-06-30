package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.UserDirectoryEntryDto;
import com.networkscanner.backend.users.dto.UserManagementDto;
import com.networkscanner.backend.users.model.RoleName;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface UserManagementService {

  List<UserDirectoryEntryDto> listDirectory();

  List<UserManagementDto> listUsers();

  UserManagementDto getUserById(Long userId);

  UserManagementDto createUser(
      String email,
      String displayName,
      String password,
      List<RoleName> roles,
      boolean enabled
  );

  UserManagementDto updateProfile(Long userId, String email, String displayName);

  UserManagementDto resetPassword(Long userId, String password);

  UserManagementDto updateRoles(Long userId, List<RoleName> roles, Authentication authentication);

  UserManagementDto updateStatus(Long userId, boolean enabled, Authentication authentication);
}
