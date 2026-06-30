package com.networkscanner.backend.config.web;

import com.networkscanner.backend.config.AppVersionResolver;
import com.networkscanner.backend.notifications.impl.TelegramBotClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/app-config")
public class AppDebugConfigController {

  private final boolean debugMode;
  private final AppVersionResolver appVersionResolver;
  private final TelegramBotClient telegramBotClient;

  public AppDebugConfigController(
      @Value("${debug.mode:false}") boolean debugMode,
      AppVersionResolver appVersionResolver,
      TelegramBotClient telegramBotClient
  ) {
    this.debugMode = debugMode;
    this.appVersionResolver = appVersionResolver;
    this.telegramBotClient = telegramBotClient;
  }

  @GetMapping
  public AppDebugConfigResponse getConfig() {
    AppVersionResolver.ResolvedAppVersion resolved = appVersionResolver.resolve();
    return new AppDebugConfigResponse(
        debugMode,
        resolved.version(),
        resolved.buildTime(),
        telegramBotClient.isReady()
    );
  }

  public record AppDebugConfigResponse(
      boolean debugMode,
      String version,
      String buildTime,
      boolean telegramReady
  ) {
  }
}
