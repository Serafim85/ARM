package com.networkscanner.backend.network.scan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DiscoveryProbeConfig(
    @NotBlank String method,
    @Min(1) @Max(65535) Integer port,
    String community,
    String securityUsername,
    String authProtocol,
    String authPassword,
    String privacyProtocol,
    String privacyPassword,
    String username,
    String password,
    String privateKeyPem,
    String passphrase,
    String clientCertPem,
    String clientKeyPem,
    Boolean insecureSkipVerify
) {
  public DiscoveryProbeConfig(
      String method,
      Integer port,
      String community,
      String securityUsername,
      String authProtocol,
      String authPassword,
      String privacyProtocol,
      String privacyPassword
  ) {
    this(
        method,
        port,
        community,
        securityUsername,
        authProtocol,
        authPassword,
        privacyProtocol,
        privacyPassword,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }
}
