package collector

import (
	"os"
	"strconv"

	"github.com/wisla/arm/agent/internal/transport"
)

func applyDemoMetricOverrides(metrics []transport.MetricPoint) []transport.MetricPoint {
	if v := os.Getenv("WISLA_ARM_DEMO_DISK_PCT"); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			metrics = setMetricValue(metrics, "arm.disk.root.used_pct", f)
		}
	}
	if v := os.Getenv("WISLA_ARM_DEMO_CPU_PCT"); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			metrics = setMetricValue(metrics, "arm.cpu.util", f)
		}
	}
	return metrics
}

func setMetricValue(metrics []transport.MetricPoint, key string, value float64) []transport.MetricPoint {
	out := make([]transport.MetricPoint, len(metrics))
	copy(out, metrics)
	for i := range out {
		if out[i].Key == key {
			out[i].Value = value
			return out
		}
	}
	return append(out, transport.MetricPoint{Key: key, Value: value})
}
