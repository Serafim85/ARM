package com.networkscanner.backend.accessprofiles.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.accessprofiles.api.AccessProfileService;
import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.network.scan.dto.DiscoveryProbeConfig;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccessProfileResolverImplTest {

  @Mock
  private AccessProfileService accessProfileService;

  @InjectMocks
  private AccessProfileResolverImpl resolver;

  @Test
  void resolveSnmpCredentials_returnsV2CommunityFromProfile() {
    AccessProfileEntity profile = profileWithSnmpV2();
    when(accessProfileService.requireEntity(7L)).thenReturn(profile);

    MonitoringSnmpCredentials credentials = resolver.resolveSnmpCredentials(7L, "SNMP_V2");

    assertNotNull(credentials);
    assertEquals("v2c", credentials.snmpVersion());
    assertEquals("private-community", credentials.community());
  }

  @Test
  void resolveScanRequest_mergesSnmpV2ProbeFromProfile() {
    AccessProfileEntity profile = profileWithSnmpV2();
    when(accessProfileService.requireEntity(7L)).thenReturn(profile);

    ScanRequest request = new ScanRequest(
        "10.0.0.0-1",
        null,
        null,
        1,
        1000,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        7L,
        List.of(new DiscoveryProbeConfig("SNMP_V2", 161, null, null, null, null, null, null))
    );

    ScanRequest resolved = resolver.resolveScanRequest(request);

    assertEquals("private-community", resolved.effectiveProbes().get(0).community());
    assertEquals(162, resolved.effectiveProbes().get(0).port());
  }

  @Test
  void resolveScanRequest_usesSeparatePortsPerSnmpVersion() {
    AccessProfileEntity profile = new AccessProfileEntity();
    profile.setId(8L);
    profile.setSnmpV1Enabled(true);
    profile.setSnmpV1Port(111);
    profile.setSnmpV1Community("v1-community");
    profile.setSnmpV2Enabled(true);
    profile.setSnmpV2Port(222);
    profile.setSnmpV2Community("v2-community");
    profile.setSnmpV3Enabled(true);
    profile.setSnmpV3Port(333);
    profile.setSnmpV3SecurityUsername("snmpuser");
    when(accessProfileService.requireEntity(8L)).thenReturn(profile);

    ScanRequest request = new ScanRequest(
        "10.0.0.0-1",
        null,
        null,
        1,
        1000,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        8L,
        List.of(
            new DiscoveryProbeConfig("SNMP_V1", null, null, null, null, null, null, null),
            new DiscoveryProbeConfig("SNMP_V2", null, null, null, null, null, null, null),
            new DiscoveryProbeConfig("SNMP_V3", null, null, null, null, null, null, null)
        )
    );

    ScanRequest resolved = resolver.resolveScanRequest(request);

    assertEquals(111, resolved.effectiveProbes().get(0).port());
    assertEquals("v1-community", resolved.effectiveProbes().get(0).community());
    assertEquals(222, resolved.effectiveProbes().get(1).port());
    assertEquals("v2-community", resolved.effectiveProbes().get(1).community());
    assertEquals(333, resolved.effectiveProbes().get(2).port());
    assertEquals("snmpuser", resolved.effectiveProbes().get(2).securityUsername());
  }

  @Test
  void validateProfileForMethods_rejectsSnmpV2WithoutProfileOrInline() {
    AccessProfileEntity profile = new AccessProfileEntity();
    profile.setId(3L);
    profile.setSnmpV2Enabled(false);
    profile.setSshEnabled(true);
    profile.setHttpsEnabled(false);
    when(accessProfileService.requireEntity(3L)).thenReturn(profile);

    assertThrows(
        ResponseStatusException.class,
        () -> resolver.validateProfileForMethods(
            3L,
            List.of(new DiscoveryProbeConfig("SNMP_V2", 161, null, null, null, null, null, null))
        )
    );
  }

  @Test
  void validateProfileForMethods_allowsInlineSnmpWhenProfileSnmpDisabled() {
    AccessProfileEntity profile = new AccessProfileEntity();
    profile.setId(3L);
    profile.setSnmpV2Enabled(false);
    profile.setSshEnabled(false);
    profile.setHttpsEnabled(false);
    when(accessProfileService.requireEntity(3L)).thenReturn(profile);

    resolver.validateProfileForMethods(
        3L,
        List.of(new DiscoveryProbeConfig("SNMP_V2", 161, "manual-community", null, null, null, null, null))
    );
  }

  private static AccessProfileEntity profileWithSnmpV2() {
    AccessProfileEntity profile = new AccessProfileEntity();
    profile.setId(7L);
    profile.setName("lab-snmp");
    profile.setSnmpV2Enabled(true);
    profile.setSnmpV2Port(162);
    profile.setSnmpV2Community("private-community");
    profile.setSshEnabled(false);
    profile.setHttpsEnabled(false);
    return profile;
  }
}
