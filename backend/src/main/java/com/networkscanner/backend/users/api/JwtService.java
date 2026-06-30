package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.model.AppUser;

public interface JwtService {

  String generateToken(AppUser user);

  String extractEmail(String token);

  boolean isTokenValid(String token);
}
