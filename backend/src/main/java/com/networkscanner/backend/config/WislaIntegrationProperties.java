package com.networkscanner.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.integration.wisla")
public class WislaIntegrationProperties {

  private String kafkaBrokerMetadataPath = "/api/wisla/kafka-broker-metadata";
  private final KafkaBrokerSecurity kafkaBrokerSecurity = new KafkaBrokerSecurity();

  public String getKafkaBrokerMetadataPath() {
    return kafkaBrokerMetadataPath;
  }

  public void setKafkaBrokerMetadataPath(String kafkaBrokerMetadataPath) {
    this.kafkaBrokerMetadataPath = kafkaBrokerMetadataPath;
  }

  public KafkaBrokerSecurity getKafkaBrokerSecurity() {
    return kafkaBrokerSecurity;
  }

  public static class KafkaBrokerSecurity {
    private String securityProtocol;
    private String saslMechanism;
    private String saslJaasConfig;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String sslKeystoreLocation;
    private String sslKeystorePassword;
    private String sslKeyPassword;

    public String getSecurityProtocol() {
      return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol) {
      this.securityProtocol = securityProtocol;
    }

    public String getSaslMechanism() {
      return saslMechanism;
    }

    public void setSaslMechanism(String saslMechanism) {
      this.saslMechanism = saslMechanism;
    }

    public String getSaslJaasConfig() {
      return saslJaasConfig;
    }

    public void setSaslJaasConfig(String saslJaasConfig) {
      this.saslJaasConfig = saslJaasConfig;
    }

    public String getSslTruststoreLocation() {
      return sslTruststoreLocation;
    }

    public void setSslTruststoreLocation(String sslTruststoreLocation) {
      this.sslTruststoreLocation = sslTruststoreLocation;
    }

    public String getSslTruststorePassword() {
      return sslTruststorePassword;
    }

    public void setSslTruststorePassword(String sslTruststorePassword) {
      this.sslTruststorePassword = sslTruststorePassword;
    }

    public String getSslKeystoreLocation() {
      return sslKeystoreLocation;
    }

    public void setSslKeystoreLocation(String sslKeystoreLocation) {
      this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public String getSslKeystorePassword() {
      return sslKeystorePassword;
    }

    public void setSslKeystorePassword(String sslKeystorePassword) {
      this.sslKeystorePassword = sslKeystorePassword;
    }

    public String getSslKeyPassword() {
      return sslKeyPassword;
    }

    public void setSslKeyPassword(String sslKeyPassword) {
      this.sslKeyPassword = sslKeyPassword;
    }
  }
}
