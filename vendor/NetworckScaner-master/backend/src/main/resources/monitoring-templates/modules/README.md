# Donor macro modules

Минимальные Zabbix export-файлы **только с секцией `macros`**. Не отображаются в UI и не участвуют в выборе шаблона устройства — используются для закрытия «дыр» в vendor/uploaded шаблонах, где триггеры ссылаются на макросы linked-модулей Zabbix без объявления в `macros:`.

## Файлы

| Файл | Donor id | Содержимое |
|------|----------|------------|
| `module_generic_snmp_macros.template` | `generic-snmp-macros` | `{$IF.UTIL.MAX}`, `{$IF.ERRORS.WARN}`, `{$IFCONTROL}`, `{$NET.IF.*}` |
| `module_vfs_fs_macros.template` | `vfs-fs-macros` | `{$VFS.FS.PUSED.*}`, `{$VFS.FS.FREE.MIN.*}`, фильтры FS discovery |
| `module_icmp_ping_macros.template` | `icmp-ping-macros` | `{$ICMP.*}`, `{$ICMP_LOSS_WARN}` |

Алиасы в реестре: техническое имя Zabbix (`Netscan module: Generic SNMP macros`), `module:generic-snmp-macros`.

## Обновление

1. Правка исходного YAML (см. `backend/scripts/template/README.md`).
2. `yaml-to-template.ps1 -Path module_*.yaml` в этой папке.
3. При изменении порогов синхронизировать [`../zabbix-module-macros.defaults.json`](../zabbix-module-macros.defaults.json) (fallback, если donor не загрузился).

## Подключение к шаблону

- **Глобально (SNMP):** `monitoring.default-macro-donors` в `application.properties`.
- **Явно:** `macroDonors: [generic-snmp-macros, vfs-fs-macros]` в `manifest.template` или manifest загруженного архива.
- **Авто:** gap inference при компиляции — если в триггерах остаются неразрешённые `{$MACRO}`, подбирается donor по префиксу (`IF.` / `VFS.FS.` / `ICMP`).

Источник значений: Zabbix 8 upstream (`template_net_generic_snmp.yaml`, `template_os_linux.yaml`, `template_module_icmp_ping.yaml`).
