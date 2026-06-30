# Golden SNMP fixtures: Linux by SNMP (wisla42)

Источник: `snmpwalk` с хоста **wisla42** (Ubuntu, net-snmp). В CI используются только JSON из этого каталога.

Полный dump в репозитории: `snmpwalk-wisla42.txt` (тот же файл, что `H:\Downloads\snmpwalk.txt`).

## Регенерация

Из каталога `backend/` (JDK 17). По умолчанию берётся bundled `snmpwalk-wisla42.txt`; другой файл — через `-Dsnmp.walk.source=...`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\corretto-17.0.15"
mvn test "-Dtest=SnmpWalkFixtureGeneratorTest#regenerateGoldenFixturesFromDump"
```

## Файлы

| Файл | Назначение |
|------|------------|
| `snmpwalk-wisla42.txt` | Исходный net-snmp dump (для перегенерации JSON) |
| `raw-oids.json` | OID → value (подмножество MIB шаблона) |
| `get-by-item-key.json` | Ответы GET items по Zabbix key |
| `walk-by-item-key.json` | JSON walk payloads для master items |
| `trigger-scenarios.json` | Сценарии triggers с metric/history overrides |

При обновлении шаблона `linux-by-snmp` (новые walk-колонки) обновите `LinuxBySnmpWalkSpecs` и перегенерируйте фикстуры.

Если в `snmpwalk` нет UCD-MIB (`1.3.6.1.4.1.2021.9`, `2021.10`), генератор подставляет минимальные synthetic walk для `system.cpu.load.walk` и `vfs.fs.walk` (см. `SnmpWalkFixtureBuilder.syntheticWalkFallbacks()`).
