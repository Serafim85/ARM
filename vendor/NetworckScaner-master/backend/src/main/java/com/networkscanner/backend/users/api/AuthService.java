package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.LoginRequest;
import com.networkscanner.backend.users.dto.LoginResponse;

public interface AuthService {

  LoginResponse login(LoginRequest request);
}
