package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.ApplicationUserDetailsService;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ApplicationUserDetailsServiceImpl implements ApplicationUserDetailsService {

  private final AppUserRepository userRepository;

  public ApplicationUserDetailsServiceImpl(AppUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AppUser user = userRepository.findByEmailIgnoreCase(username)
        .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден."));

    List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
        .toList();

    return User.withUsername(user.getEmail())
        .password(user.getPasswordHash())
        .authorities(authorities)
        .disabled(!user.isEnabled())
        .build();
  }
}
