package com.networkscanner.backend.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
public class AppVersionResolver {

  /** Суффикс версии для локальной разработки (IDEA, mvn без official.build). */
  private static final String LOCAL_BUILD_MARKER = "UNKNOWN";

  private static final DateTimeFormatter BUILD_SUFFIX_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneOffset.UTC);

  private final BuildProperties buildProperties;
  private final String configuredReleaseVersion;

  public AppVersionResolver(
      @Autowired(required = false) BuildProperties buildProperties,
      @Value("${app.release.version:}") String configuredReleaseVersion) {
    this.buildProperties = buildProperties;
    this.configuredReleaseVersion = configuredReleaseVersion;
  }

  public ResolvedAppVersion resolve() {
    if (buildProperties == null) {
      return localDevVersion(configuredReleaseVersion);
    }

    String release = buildProperties.get("release.version");
    if (release == null || release.isBlank()) {
      release = configuredReleaseVersion;
    }
    if (release == null || release.isBlank()) {
      return ResolvedAppVersion.unknown();
    }

    if (!isOfficialBuild()) {
      return localDevVersion(release);
    }

    Instant instant = buildProperties.getTime();
    if (instant == null) {
      return new ResolvedAppVersion(release, null);
    }

    String suffix = BUILD_SUFFIX_FORMAT.format(instant);
    return new ResolvedAppVersion(release + "." + suffix, instant.toString());
  }

  private boolean isOfficialBuild() {
    return "true".equalsIgnoreCase(buildProperties.get("official"));
  }

  private static ResolvedAppVersion localDevVersion(String release) {
    if (release == null || release.isBlank()) {
      return ResolvedAppVersion.unknown();
    }
    return new ResolvedAppVersion(release + "." + LOCAL_BUILD_MARKER, null);
  }

  public record ResolvedAppVersion(String version, String buildTime) {
    static ResolvedAppVersion unknown() {
      return new ResolvedAppVersion(null, null);
    }
  }
}
