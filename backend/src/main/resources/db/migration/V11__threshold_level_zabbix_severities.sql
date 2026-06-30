-- Legacy values WARN/CRITICAL → Zabbix-style severity names (see ThresholdLevel enum).
UPDATE monitoring_events SET threshold_level = 'WARNING' WHERE threshold_level = 'WARN';
UPDATE monitoring_events SET threshold_level = 'HIGH' WHERE threshold_level = 'CRITICAL';
