package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.impl.MonitoringTemplateObfuscator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DonorMacroRegistryTest {

  @Test
  void loadsDonorModulesAndResolvesByShortId() {
    DonorMacroRegistry registry = new DonorMacroRegistry(
        new MonitoringTemplateObfuscator(),
        new PathMatchingResourcePatternResolver()
    );

    Map<String, String> generic = registry.resolve("generic-snmp-macros");
    assertFalse(generic.isEmpty());
    assertEquals("90", generic.get("{$IF.UTIL.MAX}"));
    assertEquals("2", generic.get("{$IF.ERRORS.WARN}"));
    assertTrue(generic.containsKey("{$NET.IF.IFNAME.NOT_MATCHES}"));

    Map<String, String> vfs = registry.resolve("vfs-fs-macros");
    assertEquals("90", vfs.get("{$VFS.FS.PUSED.MAX.CRIT}"));
    assertEquals("5G", vfs.get("{$VFS.FS.FREE.MIN.CRIT}"));
    assertTrue(vfs.containsKey("{$VFS.FS.FSNAME.NOT_MATCHES}"));

    Map<String, String> icmp = registry.resolve("icmp-ping-macros");
    assertEquals("20", icmp.get("{$ICMP.LOSS.WARN}"));
  }

  @Test
  void resolvesByModulePrefixAndTechnicalName() {
    DonorMacroRegistry registry = new DonorMacroRegistry(
        new MonitoringTemplateObfuscator(),
        new PathMatchingResourcePatternResolver()
    );

    assertEquals(
        registry.resolve("generic-snmp-macros"),
        registry.resolve("module:generic-snmp-macros")
    );
    assertFalse(registry.resolve("Netscan module: Generic SNMP macros").isEmpty());
  }

  @Test
  void normalizesDonorRef() {
    assertEquals("generic-snmp-macros", DonorMacroRegistry.normalizeRef(" module:Generic-SNMP-Macros "));
    assertEquals("vfs-fs-macros", DonorMacroRegistry.stemToShortId("module_vfs_fs_macros"));
  }
}
