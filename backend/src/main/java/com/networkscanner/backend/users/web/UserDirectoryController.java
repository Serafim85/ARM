package com.networkscanner.backend.users.web;

import com.networkscanner.backend.users.api.UserManagementService;
import com.networkscanner.backend.users.dto.UserDirectoryEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(
    name = "Справочник пользователей",
    description = "Краткий список учётных записей для выбора в интерфейсе (например, шаринг дашбордов)."
)
public class UserDirectoryController {

  private final UserManagementService userManagementService;

  public UserDirectoryController(UserManagementService userManagementService) {
    this.userManagementService = userManagementService;
  }

  @GetMapping("/directory")
  @Operation(summary = "Список пользователей для выбора", description = "Активные пользователи: id, email, имя.")
  public List<UserDirectoryEntryDto> directory() {
    return userManagementService.listDirectory();
  }
}
