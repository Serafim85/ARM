package com.networkscanner.backend.users.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final boolean demoMonitoringSeedEnabled;
  private final boolean swaggerUiEnabled;
  private final boolean apiDocsEnabled;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      @Value("${app.demo-monitoring-seed-enabled:true}") boolean demoMonitoringSeedEnabled,
      @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled,
      @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled
  ) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.demoMonitoringSeedEnabled = demoMonitoringSeedEnabled;
    this.swaggerUiEnabled = swaggerUiEnabled;
    this.apiDocsEnabled = apiDocsEnabled;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> {
          authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
          if (swaggerUiEnabled) {
            authorize.requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**")
                .permitAll();
          }
          if (apiDocsEnabled) {
            authorize.requestMatchers(
                    "/v3/api-docs",
                    "/v3/api-docs/**")
                .permitAll();
          }
          authorize.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
          authorize.requestMatchers(HttpMethod.GET, "/api/public/app-config").permitAll();
          if (demoMonitoringSeedEnabled) {
            authorize.requestMatchers(HttpMethod.POST, "/api/debug/demo-monitoring-seed").permitAll();
          } else {
            authorize.requestMatchers(HttpMethod.POST, "/api/debug/demo-monitoring-seed").denyAll();
          }
          authorize.requestMatchers(HttpMethod.PUT, "/api/admin/system/smtp-settings/test")
              .hasRole("ADMIN");
          authorize.requestMatchers(
                  HttpMethod.GET,
                  "/api/admin/system/notification-subscriptions")
              .hasAnyRole("ADMIN", "OPERATOR");
          authorize.requestMatchers(
                  HttpMethod.POST,
                  "/api/admin/system/notification-subscriptions")
              .hasAnyRole("ADMIN", "OPERATOR");
          authorize.requestMatchers(
                  HttpMethod.POST,
                  "/api/admin/system/notification-subscriptions/test-event")
              .hasAnyRole("ADMIN", "OPERATOR");
          authorize.requestMatchers(
                  HttpMethod.DELETE,
                  "/api/admin/system/notification-subscriptions/*")
              .hasAnyRole("ADMIN", "OPERATOR");
          authorize.requestMatchers(HttpMethod.GET, "/api/access-profiles")
              .hasAnyRole("ADMIN", "OPERATOR");
          authorize.requestMatchers("/api/admin/**").hasRole("ADMIN")
              .requestMatchers(HttpMethod.POST, "/api/scan/**").hasAnyRole("ADMIN", "OPERATOR")
              .requestMatchers(HttpMethod.GET, "/api/scan/runs/**").hasAnyRole("ADMIN", "OPERATOR")
              .requestMatchers(HttpMethod.POST, "/api/monitoring/deactivate").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
              .requestMatchers(HttpMethod.POST,
                  "/api/monitoring/activate",
                  "/api/monitoring/match-scan")
              .hasAnyRole("ADMIN", "OPERATOR")
              .requestMatchers(HttpMethod.POST, "/api/monitoring/templates/upload").hasRole("ADMIN")
              .requestMatchers(HttpMethod.PATCH, "/api/monitoring/templates/*").hasRole("ADMIN")
              .requestMatchers(HttpMethod.DELETE, "/api/monitoring/templates/*").hasRole("ADMIN")
              .requestMatchers(HttpMethod.GET, "/api/integration/wisla/**").hasRole("ADMIN")
              .requestMatchers(HttpMethod.GET, "/api/wisla/**").hasAnyRole("ADMIN", "WISLA_INTEGRATION")
              .requestMatchers(HttpMethod.POST,
                  "/api/monitoring/*/backups/current-as-baseline",
                  "/api/monitoring/*/backups/baseline/upload",
                  "/api/monitoring/*/backups/baseline/select",
                  "/api/monitoring/devices/*/backups/current-as-baseline",
                  "/api/monitoring/devices/*/backups/baseline/upload",
                  "/api/monitoring/devices/*/backups/baseline/select")
              .hasRole("ADMIN")
              .requestMatchers(HttpMethod.GET, "/api/monitoring/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
              .anyRequest().authenticated();
        })
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
