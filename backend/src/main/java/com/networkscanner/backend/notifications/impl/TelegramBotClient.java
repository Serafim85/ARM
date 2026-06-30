package com.networkscanner.backend.notifications.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotClient {

  private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);

  private final String botToken;
  private final boolean enabled;
  private final HttpClient httpClient;

  public TelegramBotClient(
      @Value("${app.telegram.bot-token:}") String botToken,
      @Value("${app.telegram.enabled:false}") boolean enabled
  ) {
    this.botToken = botToken == null ? "" : botToken.trim();
    this.enabled = enabled;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public boolean isReady() {
    return enabled && !botToken.isBlank();
  }

  public void sendMessageSilently(String chatId, String text) {
    if (!isReady() || chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
      return;
    }
    try {
      String encodedChatId = URLEncoder.encode(chatId.trim(), StandardCharsets.UTF_8);
      String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
      URI uri = URI.create(
          "https://api.telegram.org/bot" + botToken + "/sendMessage?chat_id="
              + encodedChatId + "&text=" + encodedText
      );
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(15))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Telegram sendMessage failed: HTTP {} {}", response.statusCode(), response.body());
      }
    } catch (Exception ex) {
      log.warn("Telegram sendMessage error: {}", ex.getMessage());
    }
  }
}
