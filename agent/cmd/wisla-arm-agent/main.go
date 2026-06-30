package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/wisla/arm/agent/internal/collector"
	"github.com/wisla/arm/agent/internal/config"
	"github.com/wisla/arm/agent/internal/transport"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	client := transport.NewClient(cfg.ServerURL, cfg.AgentKey, cfg.RequestTimeout)
	col := collector.New()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	log.Printf("wisla-arm-agent starting host=%s server=%s interval=%s", cfg.Hostname, cfg.ServerURL, cfg.PollInterval)

	ticker := time.NewTicker(cfg.PollInterval)
	defer ticker.Stop()

	runOnce := func() {
		now := time.Now().UTC()
		snap, err := col.Collect(now)
		if err != nil {
			log.Printf("collect: %v", err)
			return
		}
		batch := transport.NewBatch(
			cfg.Hostname,
			cfg.AgentVersion,
			cfg.OSType,
			cfg.PrimaryIP,
			now,
			snap.Metrics,
			snap.Logs,
			snap.Events,
		)
		reqCtx, cancel := context.WithTimeout(ctx, cfg.RequestTimeout)
		defer cancel()
		if err := client.PostIngest(reqCtx, batch); err != nil {
			log.Printf("ingest: %v", err)
			return
		}
		log.Printf("ingest ok metrics=%d", len(snap.Metrics))
	}

	runOnce()
	for {
		select {
		case <-ctx.Done():
			log.Printf("shutdown")
			return
		case <-ticker.C:
			runOnce()
		}
	}
}
