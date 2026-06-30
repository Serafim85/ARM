package collector

import (
	"testing"
	"time"
)

func TestRealCollect(t *testing.T) {
	now := time.Date(2026, 6, 10, 12, 0, 0, 0, time.UTC)
	snap, err := Real{}.Collect(now)
	if err != nil {
		t.Fatalf("collect: %v", err)
	}
	if len(snap.Metrics) != 3 {
		t.Fatalf("expected 3 metrics, got %d", len(snap.Metrics))
	}
	for _, m := range snap.Metrics {
		if m.Value < 0 {
			t.Fatalf("negative metric %s: %v", m.Key, m.Value)
		}
	}
}

func TestFactoryStub(t *testing.T) {
	t.Setenv("WISLA_ARM_USE_STUB", "1")
	if _, ok := New().(Stub); !ok {
		t.Fatal("expected Stub collector")
	}
}
