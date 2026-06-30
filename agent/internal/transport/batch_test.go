package transport_test

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/wisla/arm/agent/internal/transport"
)

func TestNewBatch_defaultsEmptySlices(t *testing.T) {
	b := transport.NewBatch("host", "0.1.0", "linux", "", time.Unix(0, 0), nil, nil, nil)
	if b.Metrics == nil || len(b.Metrics) != 0 {
		t.Fatalf("expected empty metrics slice")
	}
	if b.Logs == nil || len(b.Logs) != 0 {
		t.Fatalf("expected empty logs slice")
	}
	if b.Events == nil || len(b.Events) != 0 {
		t.Fatalf("expected empty events slice")
	}
}

func TestIngestBatch_jsonUsesSnakeCaseAgentFields(t *testing.T) {
	ts := time.Date(2026, 6, 22, 12, 0, 0, 0, time.UTC)
	b := transport.NewBatch("pilot-linux-01", "0.1.0-dev", "linux", "10.0.0.1", ts, []transport.MetricPoint{
		{Key: "arm.cpu.util", Value: 12.5, Clock: ts},
	}, nil, nil)

	raw, err := json.Marshal(b)
	if err != nil {
		t.Fatal(err)
	}
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatal(err)
	}
	if m["agent_version"] != "0.1.0-dev" {
		t.Fatalf("agent_version missing: %v", m)
	}
	if m["hostname"] != "pilot-linux-01" {
		t.Fatalf("hostname: %v", m["hostname"])
	}
}
