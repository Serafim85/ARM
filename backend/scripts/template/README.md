# Конвертация шаблонов мониторинга: YAML ↔ `.template`

Скрипты для подготовки файлов в формате, который принимает netscan (upload и системный пакет).

## Алгоритм

Совпадает с `MonitoringTemplateObfuscator` в backend:

| Направление | Шаги |
|-------------|------|
| **YAML → `.template`** | UTF-8 текст → reverse строки → Base64 → одна ASCII-строка в файле |
| **`.template` → YAML** | trim → Base64 decode → reverse → UTF-8 YAML |

Это **обфускация**, не шифрование: при известном алгоритме содержимое восстанавливается.

## Файлы

| Скрипт | Платформа | Назначение |
|--------|-----------|------------|
| [`yaml-to-template.ps1`](yaml-to-template.ps1) | Windows PowerShell 5.1+ | YAML → `.template` |
| [`template-to-yaml.ps1`](template-to-yaml.ps1) | Windows PowerShell 5.1+ | `.template` → YAML |
| [`yaml-to-template.sh`](yaml-to-template.sh) | Linux / macOS / Git Bash | YAML → `.template` |
| [`template-to-yaml.sh`](template-to-yaml.sh) | Linux / macOS / Git Bash | `.template` → YAML |

По умолчанию после успешной конвертации **исходный файл удаляется**. Чтобы оставить его, используйте флаг `-KeepYaml` / `-KeepTemplate` (PowerShell) или `-k` (shell).

## Требования

- **PowerShell:** Windows PowerShell 5.1+ или PowerShell Core (дополнительно ничего не нужно).
- **Shell:** `bash`, `find`, **python3** (для корректной работы с UTF-8).

На Linux/macOS перед первым запуском:

```bash
chmod +x yaml-to-template.sh template-to-yaml.sh
```

## Примеры (PowerShell)

Из корня репозитория или из этой папки:

```powershell
# Один файл
.\backend\scripts\template\yaml-to-template.ps1 -Path C:\Users\user296\Documents\temp\template_os_linux_snmp_snmp.yaml

# Каталог рекурсивно (все .yaml / .yml)
.\backend\scripts\template\yaml-to-template.ps1 -Path .\backend\src\main\resources\monitoring-templates\

# Оставить исходные YAML
.\backend\scripts\template\yaml-to-template.ps1 -Path C:\templates\ -KeepYaml

# Обратно: .template → YAML
.\backend\scripts\template\template-to-yaml.ps1 -Path C:\templates\template_os_linux_snmp_snmp.template
.\backend\scripts\template\template-to-yaml.ps1 -Path .\out\ -KeepTemplate
```

## Примеры (Linux / sh)

```bash
cd backend/scripts/template

# Один файл
./yaml-to-template.sh /path/to/template_power_apc_ups_snmp.yaml

# Каталог
./yaml-to-template.sh ../../src/main/resources/monitoring-templates/

# Не удалять исходники
./yaml-to-template.sh -k /path/to/dir/

# Обратно
./template-to-yaml.sh /path/to/template_power_apc_ups_snmp.template
./template-to-yaml.sh -k ../../src/main/resources/monitoring-templates/
```

## Системный пакет в репозитории

В classpath лежат только `*.template` и `manifest.template`. Поля `file:` в manifest остаются с суффиксом `.yaml` (например `vendors/mikrotik_by_snmp.yaml`) — backend подставляет `.template` при чтении.

Типичный workflow для нового вендорного шаблона:

1. Экспорт Zabbix → `vendor_foo.yaml`
2. `yaml-to-template.sh vendor_foo.yaml` → `vendor_foo.template`
3. Добавить запись в `manifest.template` (через decode → правка YAML → encode) или обновить manifest отдельно
4. Положить `.template` в `backend/src/main/resources/monitoring-templates/`

## Donor macro modules

Каталог [`monitoring-templates/modules/`](../../src/main/resources/monitoring-templates/modules/) — обфусцированные mini-export **только macros** (без items/triggers). Backend загружает их в `DonorMacroRegistry` и подмешивает в каталог макросов SNMP-шаблонов (глобально, через `macroDonors` в manifest, или по gap inference).

Workflow обновления donor:

1. Редактировать YAML (временно) по образцу `module_generic_snmp_macros.yaml` в README modules.
2. `yaml-to-template.ps1 -Path module_*.yaml` в `monitoring-templates/modules/`.
3. При смене порогов обновить `zabbix-module-macros.defaults.json`.

Подробнее: [`modules/README.md`](../../src/main/resources/monitoring-templates/modules/README.md).

## Загрузка через UI

Админ загружает готовый `.template` (до 10 MB). В БД сохраняется **открытый** YAML (`manifest_yaml`, `template_yaml`).

## Альтернатива (Java)

Из каталога `backend/`:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.networkscanner.backend.monitoring.impl.MonitoringTemplateObfuscatorMain \
  -Dexec.args="src/main/resources/monitoring-templates"
```

Только направление YAML → `.template` для каталога или одного файла.

См. также [`../ops/encode-monitoring-templates.sh`](../ops/encode-monitoring-templates.sh) — тонкая обёртка над Java main для bundled templates.
