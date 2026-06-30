package com.networkscanner.backend.monitoring.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Column OIDs used by bundled {@code linux-by-snmp} master walk items. */
public final class LinuxBySnmpWalkSpecs {

  public static final String GET_SYSTEM_NAME_OID = "1.3.6.1.2.1.1.5.0";
  public static final String GET_SYSTEM_NAME_KEY = "system.name";

  public static final List<String> NET_IF_WALK_COLUMNS = List.of(
      "1.3.6.1.2.1.2.2.1.8",
      "1.3.6.1.2.1.2.2.1.7",
      "1.3.6.1.2.1.31.1.1.1.18",
      "1.3.6.1.2.1.31.1.1.1.1",
      "1.3.6.1.2.1.2.2.1.2",
      "1.3.6.1.2.1.2.2.1.3",
      "1.3.6.1.2.1.31.1.1.1.6",
      "1.3.6.1.2.1.31.1.1.1.10",
      "1.3.6.1.2.1.2.2.1.14",
      "1.3.6.1.2.1.2.2.1.20",
      "1.3.6.1.2.1.2.2.1.19",
      "1.3.6.1.2.1.2.2.1.13",
      "1.3.6.1.2.1.31.1.1.1.15"
  );
  public static final String NET_IF_WALK_KEY = "net.if.walk";

  public static final List<String> CPU_LOAD_WALK_COLUMNS = List.of(
      "1.3.6.1.4.1.2021.10.1.2",
      "1.3.6.1.4.1.2021.10.1.3"
  );
  public static final String CPU_LOAD_WALK_KEY = "system.cpu.load.walk";

  public static final List<String> CPU_WALK_COLUMNS = List.of(
      "1.3.6.1.2.1.25.3.3.1.1",
      "1.3.6.1.4.1.2021.11.53.0",
      "1.3.6.1.4.1.2021.11.52.0",
      "1.3.6.1.4.1.2021.11.50.0",
      "1.3.6.1.4.1.2021.11.64.0",
      "1.3.6.1.4.1.2021.11.61.0",
      "1.3.6.1.4.1.2021.11.51.0",
      "1.3.6.1.4.1.2021.11.54.0",
      "1.3.6.1.4.1.2021.11.56.0",
      "1.3.6.1.4.1.2021.11.65.0",
      "1.3.6.1.4.1.2021.11.66.0"
  );
  public static final String CPU_WALK_KEY = "system.cpu.walk";

  public static final List<String> VFS_FS_WALK_COLUMNS = List.of(
      "1.3.6.1.4.1.2021.9.1.1",
      "1.3.6.1.4.1.2021.9.1.2",
      "1.3.6.1.4.1.2021.9.1.3",
      "1.3.6.1.4.1.2021.9.1.10",
      "1.3.6.1.4.1.2021.9.1.11",
      "1.3.6.1.4.1.2021.9.1.12",
      "1.3.6.1.4.1.2021.9.1.13",
      "1.3.6.1.4.1.2021.9.1.14",
      "1.3.6.1.4.1.2021.9.1.15",
      "1.3.6.1.4.1.2021.9.1.16"
  );
  public static final String VFS_FS_WALK_KEY = "vfs.fs.walk";

  private static final Map<String, String> WALK_OID_FIELD_ALIASES = Map.of(
      "1.3.6.1.4.1.2021.10.1.2", "laName",
      "1.3.6.1.4.1.2021.10.1.3", "laLoad",
      "1.3.6.1.4.1.2021.9.1.1", "index",
      "1.3.6.1.4.1.2021.9.1.2", "dskPath",
      "1.3.6.1.4.1.2021.9.1.3", "dskDevice"
  );

  private LinuxBySnmpWalkSpecs() {
  }

  public static Map<String, List<String>> allWalkItems() {
    Map<String, List<String>> walks = new LinkedHashMap<>();
    walks.put(NET_IF_WALK_KEY, NET_IF_WALK_COLUMNS);
    walks.put(CPU_LOAD_WALK_KEY, CPU_LOAD_WALK_COLUMNS);
    walks.put(CPU_WALK_KEY, CPU_WALK_COLUMNS);
    walks.put(VFS_FS_WALK_KEY, VFS_FS_WALK_COLUMNS);
    return walks;
  }

  public static String fieldNameForColumn(String columnOid, int index) {
    return WALK_OID_FIELD_ALIASES.getOrDefault(columnOid, "col" + (index + 1));
  }
}
