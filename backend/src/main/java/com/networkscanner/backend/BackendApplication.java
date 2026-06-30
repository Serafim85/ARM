package com.networkscanner.backend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, normalizeArgs(args));
  }

  /**
   * Tolerate an operational misconfiguration where -Dspring.profiles.active is mistakenly passed
   * after -jar in systemd ExecStart; in that case it reaches application args, not JVM args.
   */
  static String[] normalizeArgs(String[] args) {
    String javaProfileProperty = System.getProperty("spring.profiles.active");
    List<String> normalized = new ArrayList<>(args == null ? 0 : args.length);
    boolean hasSpringArgProfile = false;

    if (args != null) {
      for (String arg : args) {
        if (arg != null && arg.startsWith("--spring.profiles.active=")) {
          hasSpringArgProfile = true;
        }
      }
      for (String arg : args) {
        if (arg == null || !arg.startsWith("-Dspring.profiles.active=")) {
          normalized.add(arg);
          continue;
        }
        if (javaProfileProperty == null || javaProfileProperty.isBlank()) {
          String value = arg.substring("-Dspring.profiles.active=".length());
          if (!value.isBlank() && !hasSpringArgProfile) {
            System.setProperty("spring.profiles.active", value);
          }
        }
      }
    }

    return normalized.toArray(String[]::new);
  }
}
