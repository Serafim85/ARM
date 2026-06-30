package com.networkscanner.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class AppVersionResolverTest {

  @Test
  void resolveReturnsUnknownWhenBuildPropertiesAndConfiguredReleaseMissing() {
    AppVersionResolver resolver = new AppVersionResolver(null, "");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertNull(resolved.version());
    assertNull(resolved.buildTime());
  }

  @Test
  void resolveReturnsConfiguredReleaseWhenBuildPropertiesMissing() {
    AppVersionResolver resolver = new AppVersionResolver(null, "1.0.0");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertEquals("1.0.0.UNKNOWN", resolved.version());
    assertNull(resolved.buildTime());
  }

  @Test
  void resolveBuildsFullVersionForOfficialCiBuild() {
    Properties properties = new Properties();
    properties.setProperty("time", "2026-05-19T01:03:00Z");
    properties.setProperty("release.version", "5.2.17");
    properties.setProperty("official", "true");
    AppVersionResolver resolver = new AppVersionResolver(new BuildProperties(properties), "");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertEquals("5.2.17.2605190103", resolved.version());
    assertEquals("2026-05-19T01:03:00Z", resolved.buildTime());
  }

  @Test
  void resolveReturnsReleaseOnlyForLocalBuild() {
    Properties properties = new Properties();
    properties.setProperty("time", "2026-05-19T01:03:00Z");
    properties.setProperty("release.version", "1.0.0");
    properties.setProperty("official", "false");
    AppVersionResolver resolver = new AppVersionResolver(new BuildProperties(properties), "9.9.9");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertEquals("1.0.0.UNKNOWN", resolved.version());
    assertNull(resolved.buildTime());
  }

  @Test
  void resolveAppendsUnknownWhenOfficialFlagMissing() {
    Properties properties = new Properties();
    properties.setProperty("time", "2026-05-19T01:03:00Z");
    properties.setProperty("release.version", "1.0.0");
    AppVersionResolver resolver = new AppVersionResolver(new BuildProperties(properties), "");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertEquals("1.0.0.UNKNOWN", resolved.version());
    assertNull(resolved.buildTime());
  }

  @Test
  void resolveReturnsUnknownWhenReleaseVersionMissing() {
    Properties properties = new Properties();
    properties.setProperty("time", "2026-05-19T01:03:00Z");
    properties.setProperty("official", "true");
    AppVersionResolver resolver = new AppVersionResolver(new BuildProperties(properties), "");

    AppVersionResolver.ResolvedAppVersion resolved = resolver.resolve();

    assertNull(resolved.version());
    assertNull(resolved.buildTime());
  }
}
