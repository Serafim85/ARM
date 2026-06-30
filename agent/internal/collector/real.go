package collector

import (
	"fmt"
	"os/user"
	"runtime"
	"time"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/mem"
	"github.com/wisla/arm/agent/internal/transport"
)

// Real collects host telemetry via gopsutil (Linux + Windows).
type Real struct{}

func (Real) Collect(now time.Time) (Snapshot, error) {
	cpuValues, err := cpu.Percent(0, false)
	if err != nil || len(cpuValues) == 0 {
		return Snapshot{}, fmt.Errorf("cpu: %w", err)
	}

	vm, err := mem.VirtualMemory()
	if err != nil {
		return Snapshot{}, fmt.Errorf("memory: %w", err)
	}

	rootMount := rootMountPoint()
	du, err := disk.Usage(rootMount)
	if err != nil {
		return Snapshot{}, fmt.Errorf("disk %s: %w", rootMount, err)
	}

	metrics := []transport.MetricPoint{
		{Key: "arm.cpu.util", Value: cpuValues[0], Clock: now},
		{Key: "arm.mem.used", Value: float64(vm.Used), Clock: now},
		{Key: "arm.disk.root.used_pct", Value: du.UsedPercent, Clock: now},
	}
	metrics = applyDemoMetricOverrides(metrics)

	logs := hostLogs(now)
	return Snapshot{
		Metrics: metrics,
		Logs:    logs,
		Events:  demoBsodEvent(now),
	}, nil
}

func rootMountPoint() string {
	if runtime.GOOS == "windows" {
		return "C:"
	}
	return "/"
}

func hostLogs(now time.Time) []transport.LogEntry {
	username := currentUsername()
	if username == "" {
		return nil
	}
	return []transport.LogEntry{
		{
			Level:   "warning",
			Message: fmt.Sprintf("Agent heartbeat: active user %s on %s", username, runtime.GOOS),
			Clock:   &now,
			Source:  "wisla-arm-agent",
		},
	}
}

func currentUsername() string {
	u, err := user.Current()
	if err != nil || u == nil {
		return ""
	}
	if u.Username != "" {
		return u.Username
	}
	return u.Name
}
