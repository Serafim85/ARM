package com.networkscanner.backend.network.scan.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.network.scan.dto.SnmpDeviceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnmpDeviceTypeClassifierTest {

  @Test
  void classifiesRouterByForwardingAndInternetLayer() {
    assertEquals(SnmpDeviceType.ROUTER, SnmpDeviceTypeClassifier.classify(4, 1).orElseThrow());
    assertEquals(SnmpDeviceType.ROUTER, SnmpDeviceTypeClassifier.classify(4, 2).orElseThrow());
  }

  @Test
  void classifiesSwitchByDatalinkLayerWithoutRouting() {
    assertEquals(SnmpDeviceType.SWITCH, SnmpDeviceTypeClassifier.classify(2, 2).orElseThrow());
    assertEquals(SnmpDeviceType.SWITCH, SnmpDeviceTypeClassifier.classify(6, 2).orElseThrow());
  }

  @Test
  void classifiesHybridWhenForwardingAndDatalinkPresent() {
    assertEquals(SnmpDeviceType.HYBRID, SnmpDeviceTypeClassifier.classify(6, 1).orElseThrow());
  }

  @Test
  void classifiesHostByApplicationLayers() {
    assertEquals(SnmpDeviceType.HOST, SnmpDeviceTypeClassifier.classify(72, 2).orElseThrow());
    assertEquals(SnmpDeviceType.HOST, SnmpDeviceTypeClassifier.classify(null, 2).orElseThrow());
  }

  @Test
  void prefersHostWhenHostLayersPresentWithoutSwitching() {
    assertEquals(SnmpDeviceType.HOST, SnmpDeviceTypeClassifier.classify(76, 2).orElseThrow());
  }

  @Test
  void resolvesTagsFromSnmpValuesWithFormattedIntegers() {
    List<String> tags = SnmpDeviceTypeClassifier.resolveTags(Map.of(
        "sysServices", "6",
        "ipForwarding", "forwarding(1)"
    ));
    assertEquals(List.of("Гибрид"), tags);
  }

  @Test
  void mergeDeviceTypeTagReplacesPreviousAutoTagAndKeepsUserTags() {
    List<String> merged = SnmpDeviceTypeClassifier.mergeDeviceTypeTag(
        List.of("Коммутатор", "ЦОД", " edge "),
        List.of("Маршрутизатор")
    );
    assertEquals(List.of("ЦОД", "edge", "Маршрутизатор"), merged);
  }

  @Test
  void mergeDeviceTypeTagRemovesAutoTagWhenScanDidNotDetectType() {
    List<String> merged = SnmpDeviceTypeClassifier.mergeDeviceTypeTag(
        List.of("Коммутатор", "ЦОД"),
        List.of()
    );
    assertEquals(List.of("ЦОД"), merged);
  }

  @Test
  void recognizesDeviceTypeTags() {
    assertTrue(SnmpDeviceTypeClassifier.isDeviceTypeTag("Маршрутизатор"));
    assertFalse(SnmpDeviceTypeClassifier.isDeviceTypeTag("ЦОД"));
  }
}
