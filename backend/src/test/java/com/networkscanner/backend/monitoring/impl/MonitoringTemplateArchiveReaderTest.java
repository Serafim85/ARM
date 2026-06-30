package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.UploadedMonitoringTemplatePackage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitoringTemplateArchiveReaderTest {

  private MonitoringTemplateObfuscator obfuscator;
  private MonitoringTemplateArchiveReader reader;

  @BeforeEach
  void setUp() {
    obfuscator = new MonitoringTemplateObfuscator();
    reader = new MonitoringTemplateArchiveReader(obfuscator);
  }

  @Test
  void readsTemplateFile() {
    String yaml = """
        zabbix_export:
          version: "8.0"
          templates:
            - template: "Linux by SNMP"
              name: "Linux by SNMP"
              items: []
        """;

    UploadedMonitoringTemplatePackage uploaded = reader.readSingleTemplatePackage(
        "template_os_linux_snmp_snmp.template",
        encode(yaml)
    );

    assertEquals("linux-by-snmp", uploaded.templateId());
    assertEquals("linux-by-snmp.yaml", uploaded.templateFileName());
    assertTrue(uploaded.manifestYaml().contains("defaultTemplateId: \"linux-by-snmp\""));
  }

  @Test
  void rejectsYamlAndZipExtensions() {
    String yaml = "zabbix_export:\n  version: \"8.0\"\n  templates: []\n";
    assertThrows(
        IllegalArgumentException.class,
        () -> reader.readSingleTemplatePackage("export.yaml", yaml.getBytes(StandardCharsets.UTF_8))
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> reader.readSingleTemplatePackage("archive.zip", new byte[] {1, 2, 3})
    );
  }

  @Test
  void rejectsOversizedTemplate() {
    byte[] oversized = new byte[MonitoringTemplateArchiveReader.MAX_TEMPLATE_FILE_BYTES + 1];
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> reader.readSingleTemplatePackage("big.template", oversized)
    );
    assertTrue(exception.getMessage().contains("10 MB"));
  }

  @Test
  void rejectsCorruptTemplate() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> reader.readSingleTemplatePackage("bad.template", "not-base64".getBytes(StandardCharsets.UTF_8))
    );
    assertEquals("Повреждён файл шаблона", exception.getMessage());
  }

  @Test
  void readsTemplateWithMultipleTemplates_selectsChildWithLinkedParents() {
    String yaml = """
        zabbix_export:
          version: "6.0"
          templates:
            - template: ParentA
              name: ParentA
              items: []
            - template: ChildMain
              name: ChildMain
              templates:
                - name: ParentA
                - name: ParentB
              items:
                - uuid: item-1
                  name: x
                  type: SNMP_AGENT
                  snmp_oid: .1.3.6.1.2.1.1.5.0
                  key: sysName
                  delay: "60"
            - template: ParentB
              name: ParentB
              items: []
        """;

    UploadedMonitoringTemplatePackage uploaded =
        reader.readSingleTemplatePackage("export.template", encode(yaml));

    assertEquals("childmain", uploaded.templateId());
    assertTrue(uploaded.manifestYaml().contains("zabbixTemplate: \"ChildMain\""));
  }

  @Test
  void readsTemplateWithMultipleTemplates_matchesFilenameStem() {
    String yaml = """
        zabbix_export:
          version: "6.0"
          templates:
            - template: Other
              name: Other
              items: []
            - template: My_Device
              name: My_Device
              items:
                - uuid: item-1
                  name: x
                  type: SNMP_AGENT
                  snmp_oid: .1.3.6.1.2.1.1.5.0
                  key: sysName
                  delay: "60"
        """;

    UploadedMonitoringTemplatePackage uploaded = reader.readSingleTemplatePackage(
        "template_my_device.template",
        encode(yaml)
    );

    assertEquals("my-device", uploaded.templateId());
    assertTrue(uploaded.manifestYaml().contains("zabbixTemplate: \"My_Device\""));
  }

  @Test
  void rejectsAmbiguousTemplateWhenSeveralLinkedAndNoFilenameMatch() {
    String yaml = """
        zabbix_export:
          version: "6.0"
          templates:
            - template: A
              name: A
              templates:
                - name: X
              items: []
            - template: B
              name: B
              templates:
                - name: Y
              items: []
        """;

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> reader.readSingleTemplatePackage("vague.template", encode(yaml))
    );

    assertTrue(exception.getMessage().contains("одним целевым шаблоном"));
  }

  private byte[] encode(String yaml) {
    return obfuscator.encodeUtf8(yaml).getBytes(StandardCharsets.UTF_8);
  }
}
