package com.networkscanner.backend.users.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.AuthService;
import com.networkscanner.backend.users.dto.LoginRequest;
import com.networkscanner.backend.users.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Аутентификация", description = "Вход в систему и выдача JWT")
public class AuthController {

  private final AuthService authService;
  private final AuditLogService auditLogService;

  public AuthController(AuthService authService, AuditLogService auditLogService) {
    this.authService = authService;
    this.auditLogService = auditLogService;
  }

  @SecurityRequirements
  @PostMapping("/login")
  @Operation(
      summary = "Вход",
      description = "Проверяет email и пароль. При успехе возвращает JWT (accessToken) и данные пользователя; "
          + "используйте accessToken в заголовке Authorization: Bearer <token> для остальных запросов."
  )
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      return ResponseEntity.ok(authService.login(request));
    } catch (RuntimeException exception) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new LoginResponse("Неверный email или пароль.", null, null, null, java.util.List.of(), null, null, null));
    }
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Выход",
      description = "Фиксирует выход пользователя в журнале аудита. Клиент должен удалить JWT локально."
  )
  public ResponseEntity<Void> logout(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
      auditLogService.record(
          authentication,
          AuditCategory.AUTH_SESSION,
          AuditAction.LOGOUT,
          authentication.getName(),
          "Выход из системы."
      );
    }
    return ResponseEntity.noContent().build();
  }
}
