package collector

import (
	"math"
	"os"
	"time"

	"github.com/wisla/arm/agent/internal/transport"
)

type Snapshot struct {
	Metrics []transport.MetricPoint
	Logs    []transport.LogEntry
	Events  []transport.EventEntry
}

type Collector interface {
	Collect(now time.Time) (Snapshot, error)
}

type Stub struct{}

func (Stub) Collect(now time.Time) (Snapshot, error) {
	// MVP stub: plausible values for UI charts (not real host telemetry yet).
	phase := float64(now.Unix() % 120)
	cpu := 8 + 6*math.Sin(phase/12)
	memUsed := float64(6*1024*1024*1024) * (0.42 + 0.08*math.Sin(phase/20))
	diskPct := 38 + 4*math.Sin(phase/30)

	return Snapshot{
		Metrics: []transport.MetricPoint{
			{Key: "arm.cpu.util", Value: cpu, Clock: now},
			{Key: "arm.mem.used", Value: memUsed, Clock: now},
			{Key: "arm.disk.root.used_pct", Value: diskPct, Clock: now},
		},
		Logs: []transport.LogEntry{
			{
				Level:   "warning",
				Message: "Stub collector: elevated memory pressure (demo)",
				Clock:   &now,
				Source:  "wisla-arm-agent/stub",
			},
		},
		Events: demoBsodEvent(now),
	}, nil
}

func demoBsodEvent(now time.Time) []transport.EventEntry {
	if os.Getenv("WISLA_ARM_DEMO_BSOD") != "1" {
		return nil
	}
	return []transport.EventEntry{
		{
			Type:      "BSOD",
			Message:   "Unexpected shutdown detected (demo event)",
			Clock:     &now,
			Severity:  "HIGH",
			ErrorCode: "0x000000EF",
			ErrorText: "CRITICAL_PROCESS_DIED",
			Source:    "wisla-arm-agent/stub",
		},
	}
}
