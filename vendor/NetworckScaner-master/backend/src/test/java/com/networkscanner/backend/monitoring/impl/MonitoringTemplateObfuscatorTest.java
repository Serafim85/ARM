package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitoringTemplateObfuscatorTest {

  private MonitoringTemplateObfuscator obfuscator;

  @BeforeEach
  void setUp() {
    obfuscator = new MonitoringTemplateObfuscator();
  }

  @Test
  void roundTripPreservesYaml() {
    String plain = """
        zabbix_export:
          version: '8.0'
          templates:
            - template: 'Тест'
        """;
    String encoded = obfuscator.encodeUtf8(plain);
    assertEquals(plain, obfuscator.decodeUtf8(encoded));
  }

  @Test
  void decodeFromFileBytes() {
    String plain = "zabbix_export:\n  version: '1.0'\n";
    byte[] fileBytes = obfuscator.encodeUtf8(plain).getBytes(StandardCharsets.UTF_8);
    assertEquals(plain, obfuscator.decodeUtf8(fileBytes));
  }

  @Test
  void rejectsEmptyAndInvalidBase64() {
    assertThrows(IllegalArgumentException.class, () -> obfuscator.decodeUtf8(""));
    assertThrows(IllegalArgumentException.class, () -> obfuscator.decodeUtf8("not-valid-base64!!!"));
    assertThrows(
        IllegalArgumentException.class,
        () -> obfuscator.decodeUtf8(new byte[0])
    );
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> obfuscator.decodeUtf8("@@@"));
    assertEquals(MonitoringTemplateObfuscator.CORRUPT_FILE_MESSAGE, exception.getMessage());
  }
}
