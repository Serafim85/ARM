package com.networkscanner.backend.monitoring.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * CLI: encode YAML monitoring templates to {@code .template} files (reverse + Base64).
 *
 * <p>Usage: {@code MonitoringTemplateObfuscatorMain <input.yaml|directory> [output.template]}
 */
public final class MonitoringTemplateObfuscatorMain {

  private MonitoringTemplateObfuscatorMain() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: MonitoringTemplateObfuscatorMain <input.yaml|directory> [output.template]");
      System.exit(1);
      return;
    }
    Path input = Path.of(args[0]);
    MonitoringTemplateObfuscator obfuscator = new MonitoringTemplateObfuscator();
    if (Files.isDirectory(input)) {
      encodeDirectory(input, obfuscator);
      return;
    }
    if (!Files.isRegularFile(input)) {
      System.err.println("Not found: " + input);
      System.exit(1);
      return;
    }
    Path output = args.length == 2
        ? Path.of(args[1])
        : Path.of(resolveTemplateFileName(input.getFileName().toString()));
    encodeFile(input, output, obfuscator);
  }

  private static void encodeDirectory(Path directory, MonitoringTemplateObfuscator obfuscator) throws IOException {
    try (Stream<Path> yamlFiles = Files.walk(directory)) {
      yamlFiles
          .filter(Files::isRegularFile)
          .filter(MonitoringTemplateObfuscatorMain::isYamlFile)
          .forEach(yamlPath -> {
            try {
              Path output = yamlPath.resolveSibling(resolveTemplateFileName(yamlPath.getFileName().toString()));
              encodeFile(yamlPath, output, obfuscator);
              System.out.println(yamlPath + " -> " + output);
            } catch (IOException exception) {
              throw new IllegalStateException("Failed to encode " + yamlPath, exception);
            }
          });
    }
  }

  private static void encodeFile(Path input, Path output, MonitoringTemplateObfuscator obfuscator) throws IOException {
    String plain = Files.readString(input, StandardCharsets.UTF_8);
    String encoded = obfuscator.encodeUtf8(plain);
    Files.createDirectories(output.getParent() == null ? Path.of(".") : output.getParent());
    Files.writeString(output, encoded, StandardCharsets.US_ASCII);
  }

  private static boolean isYamlFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".yaml") || name.endsWith(".yml");
  }

  static String resolveTemplateFileName(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".yaml")) {
      return fileName.substring(0, fileName.length() - 5) + ".template";
    }
    if (lower.endsWith(".yml")) {
      return fileName.substring(0, fileName.length() - 4) + ".template";
    }
    if (lower.endsWith(".template")) {
      return fileName;
    }
    return fileName + ".template";
  }
}
