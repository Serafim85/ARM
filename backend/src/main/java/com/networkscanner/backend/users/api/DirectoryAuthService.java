package com.networkscanner.backend.users.api;

import java.util.List;

public interface DirectoryAuthService {

  DirectoryAuthResult authenticate(String login, String password);

  record DirectoryAuthResult(
      DirectoryAuthStatus status,
      String login,
      String email,
      String displayName,
      List<String> groupDns,
      String failureReason,
      boolean allowLocalFallback
  ) {
    public static DirectoryAuthResult disabled() {
      return new DirectoryAuthResult(DirectoryAuthStatus.DISABLED, null, null, null, List.of(), null, true);
    }
  }

  enum DirectoryAuthStatus {
    DISABLED,
    SUCCESS,
    INVALID_CREDENTIALS,
    DIRECTORY_UNAVAILABLE,
    UNSUPPORTED_AUTH_TYPE,
    FILTER_REJECTED
  }
}
