package com.networkscanner.backend.monitoring.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Obfuscates monitoring template YAML for {@code .template} files:
 * UTF-8 plaintext → reverse string → Base64 (single-line text file).
 */
@Component
public class MonitoringTemplateObfuscator {

  static final String CORRUPT_FILE_MESSAGE = "Повреждён файл шаблона";

  public String encodeUtf8(String plainYaml) {
    if (plainYaml == null) {
      throw new IllegalArgumentException(CORRUPT_FILE_MESSAGE);
    }
    String reversed = new StringBuilder(plainYaml).reverse().toString();
    return Base64.getEncoder().encodeToString(reversed.getBytes(StandardCharsets.UTF_8));
  }

  public String decodeUtf8(byte[] fileBytes) {
    if (fileBytes == null || fileBytes.length == 0) {
      throw new IllegalArgumentException(CORRUPT_FILE_MESSAGE);
    }
    return decodeUtf8(new String(fileBytes, StandardCharsets.UTF_8));
  }

  public String decodeUtf8(String base64Content) {
    if (base64Content == null || base64Content.isBlank()) {
      throw new IllegalArgumentException(CORRUPT_FILE_MESSAGE);
    }
    try {
      byte[] reversedBytes = Base64.getDecoder().decode(base64Content.trim());
      String reversed = new String(reversedBytes, StandardCharsets.UTF_8);
      return new StringBuilder(reversed).reverse().toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(CORRUPT_FILE_MESSAGE, exception);
    }
  }
}
