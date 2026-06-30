package com.networkscanner.backend.topology.impl;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Общая проверка загрузки фона слоя (PNG/JPEG/SVG) и нормализация hex-цветов.
 */
final class LayerBackgroundSupport {

  /** Согласовано с {@code spring.servlet.multipart.max-file-size}. */
  static final int MAX_LAYER_BACKGROUND_BYTES = 5 * 1024 * 1024;

  private static final String LAYER_BG_PNG = "image/png";
  private static final String LAYER_BG_JPEG = "image/jpeg";
  private static final String LAYER_BG_SVG = "image/svg+xml";

  private static final Pattern FRAME_BORDER_HEX = Pattern.compile("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$");

  private LayerBackgroundSupport() {}

  static String resolveLayerBackgroundContentType(byte[] bytes, String declaredContentType) {
    if (bytes == null || bytes.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустой файл.");
    }
    if (bytes.length > MAX_LAYER_BACKGROUND_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Файл слишком больший (максимум " + (MAX_LAYER_BACKGROUND_BYTES / (1024 * 1024)) + " МБ)."
      );
    }
    String detected = detectLayerBackgroundMime(bytes);
    if (detected == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Допустимы только PNG, JPEG и SVG.");
    }
    if (declaredContentType != null && !declaredContentType.isBlank()) {
      String d = normalizeDeclaredMime(declaredContentType);
      if (!"application/octet-stream".equals(d)
          && !d.equals(detected)
          && !layerBackgroundDeclaredCompatible(d, detected)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Тип файла не совпадает с содержимым (ожидался формат, соответствующий " + detected + ")."
        );
      }
    }
    return detected;
  }

  static String normalizeHexColorOrThrow(String raw) {
    if (!FRAME_BORDER_HEX.matcher(raw).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Укажите цвет в формате #RRGGBB или #RGB."
      );
    }
    if (raw.length() == 4) {
      return ("#" + raw.charAt(1) + raw.charAt(1) + raw.charAt(2) + raw.charAt(2) + raw.charAt(3) + raw.charAt(3))
          .toLowerCase(Locale.ROOT);
    }
    return raw.toLowerCase(Locale.ROOT);
  }

  private static String normalizeDeclaredMime(String raw) {
    String s = raw.strip().toLowerCase(Locale.ROOT);
    int semi = s.indexOf(';');
    return semi < 0 ? s : s.substring(0, semi).trim();
  }

  private static boolean layerBackgroundDeclaredCompatible(String declaredLower, String detected) {
    if (LAYER_BG_JPEG.equals(detected) && ("image/jpg".equals(declaredLower) || "image/pjpeg".equals(declaredLower))) {
      return true;
    }
    return declaredLower.equals(detected);
  }

  private static String detectLayerBackgroundMime(byte[] b) {
    if (b.length >= 8
        && b[0] == (byte) 0x89
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && b[4] == 0x0D
        && b[5] == 0x0A
        && b[6] == 0x1A
        && b[7] == 0x0A) {
      return LAYER_BG_PNG;
    }
    if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
      return LAYER_BG_JPEG;
    }
    if (looksLikeSvgDocument(b)) {
      return LAYER_BG_SVG;
    }
    return null;
  }

  private static boolean looksLikeSvgDocument(byte[] b) {
    int start = 0;
    if (b.length >= 3 && b[0] == (byte) 0xEF && b[1] == (byte) 0xBB && b[2] == (byte) 0xBF) {
      start = 3;
    }
    int len = Math.min(b.length - start, 65536);
    if (len <= 0) {
      return false;
    }
    String head = new String(b, start, len, StandardCharsets.UTF_8);
    int lt = head.indexOf('<');
    if (lt < 0) {
      return false;
    }
    String tail = head.substring(lt).toLowerCase(Locale.ROOT);
    return tail.startsWith("<svg") || (tail.startsWith("<?xml") && tail.contains("<svg"));
  }
}
