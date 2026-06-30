package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.UserManagementService;
import com.networkscanner.backend.users.dto.UserDirectoryEntryDto;
import com.networkscanner.backend.users.dto.UserManagementDto;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserManagementServiceImpl implements UserManagementService {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserManagementServiceImpl(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public List<UserDirectoryEntryDto> listDirectory() {
    return userRepository.findAll().stream()
        .filter(AppUser::isEnabled)
        .sorted(Comparator.comparing(AppUser::getDisplayName, String.CASE_INSENSITIVE_ORDER))
        .map(u -> new UserDirectoryEntryDto(u.getId(), u.getEmail(), u.getDisplayName()))
        .toList();
  }

  @Override
  public List<UserManagementDto> listUsers() {
    return userRepository.findAll().stream()
        .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
        .map(this::toDto)
        .toList();
  }

  @Override
  public UserManagementDto getUserById(Long userId) {
    return toDto(getUser(userId));
  }

  @Override
  public UserManagementDto createUser(
      String email,
      String displayName,
      String password,
      List<RoleName> roles,
      boolean enabled
  ) {
    String normalizedEmail = email.trim().toLowerCase();
    if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      throw new IllegalArgumentException("Пользователь с таким email уже существует.");
    }

    Set<RoleName> normalizedRoles = new LinkedHashSet<>(roles);
    if (normalizedRoles.isEmpty()) {
      throw new IllegalArgumentException("У пользователя должна быть хотя бы одна роль.");
    }

    AppUser user = new AppUser();
    user.setEmail(normalizedEmail);
    user.setDisplayName(displayName.trim());
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRoles(normalizedRoles);
    user.setEnabled(enabled);
    user.setCreatedAt(OffsetDateTime.now());
    return toDto(userRepository.save(user));
  }

  @Override
  public UserManagementDto updateProfile(Long userId, String email, String displayName) {
    AppUser user = getUser(userId);
    String normalizedEmail = email.trim().toLowerCase();
    AppUser existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
    if (existingUser != null && !existingUser.getId().equals(userId)) {
      throw new IllegalArgumentException("Пользователь с таким email уже существует.");
    }

    user.setEmail(normalizedEmail);
    user.setDisplayName(displayName.trim());
    return toDto(userRepository.save(user));
  }

  @Override
  public UserManagementDto resetPassword(Long userId, String password) {
    AppUser user = getUser(userId);
    user.setPasswordHash(passwordEncoder.encode(password));
    return toDto(userRepository.save(user));
  }

  @Override
  public UserManagementDto updateRoles(Long userId, List<RoleName> roles, Authentication authentication) {
    AppUser user = getUser(userId);
    String currentEmail = authentication.getName();

    Set<RoleName> normalizedRoles = new LinkedHashSet<>(roles);
    if (normalizedRoles.isEmpty()) {
      throw new IllegalArgumentException("У пользователя должна оставаться хотя бы одна роль.");
    }

    if (user.getEmail().equalsIgnoreCase(currentEmail) && !normalizedRoles.contains(RoleName.ADMIN)) {
      throw new IllegalArgumentException("Нельзя снять роль ADMIN у текущего администратора.");
    }

    user.setRoles(normalizedRoles);
    return toDto(userRepository.save(user));
  }

  @Override
  public UserManagementDto updateStatus(Long userId, boolean enabled, Authentication authentication) {
    AppUser user = getUser(userId);
    String currentEmail = authentication.getName();

    if (user.getEmail().equalsIgnoreCase(currentEmail) && !enabled) {
      throw new IllegalArgumentException("Нельзя заблокировать собственную учетную запись.");
    }

    user.setEnabled(enabled);
    return toDto(userRepository.save(user));
  }

  private AppUser getUser(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден."));
  }

  private UserManagementDto toDto(AppUser user) {
    return new UserManagementDto(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.isEnabled(),
        user.getCreatedAt().format(DATE_TIME_FORMATTER),
        user.getRoles().stream().map(Enum::name).toList()
    );
  }
}
