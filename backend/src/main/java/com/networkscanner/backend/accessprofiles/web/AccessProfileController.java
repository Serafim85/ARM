package com.networkscanner.backend.accessprofiles.web;

import com.networkscanner.backend.accessprofiles.api.AccessProfileService;
import com.networkscanner.backend.accessprofiles.dto.AccessProfileSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access-profiles")
@Tag(name = "Профили доступа", description = "Список профилей для сканирования (ADMIN, OPERATOR)")
public class AccessProfileController {

  private final AccessProfileService service;

  public AccessProfileController(AccessProfileService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Список профилей доступа")
  public List<AccessProfileSummaryDto> list() {
    return service.listSummaries();
  }
}
