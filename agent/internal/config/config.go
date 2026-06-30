package config

import (
	"errors"
	"fmt"
	"os"
	"runtime"
	"strconv"
	"time"
)

const (
	defaultServerURL   = "http://localhost:8081"
	defaultInterval    = 60 * time.Second
	defaultAgentKeyEnv = "WISLA_ARM_AGENT_KEY"
)

type Config struct {
	ServerURL      string
	AgentKey       string
	Hostname       string
	OSType         string
	PrimaryIP      string
	AgentVersion   string
	PollInterval   time.Duration
	RequestTimeout time.Duration
}

func Load() (Config, error) {
	key := os.Getenv(defaultAgentKeyEnv)
	if key == "" {
		key = os.Getenv("AGENT_INGEST_API_KEY")
	}
	if key == "" {
		return Config{}, fmt.Errorf("set %s or AGENT_INGEST_API_KEY", defaultAgentKeyEnv)
	}

	hostname := envOr("WISLA_ARM_HOSTNAME", "")
	if hostname == "" {
		var err error
		hostname, err = os.Hostname()
		if err != nil || hostname == "" {
			return Config{}, errors.New("resolve hostname")
		}
	}

	cfg := Config{
		ServerURL:      envOr("WISLA_ARM_SERVER_URL", defaultServerURL),
		AgentKey:       key,
		Hostname:       hostname,
		OSType:         envOr("WISLA_ARM_OS_TYPE", defaultOSType()),
		AgentVersion:   envOr("WISLA_ARM_AGENT_VERSION", "0.1.0-dev"),
		PrimaryIP:      envOr("WISLA_ARM_PRIMARY_IP", ""),
		PollInterval:   durationEnv("WISLA_ARM_POLL_INTERVAL_SEC", 60) * time.Second,
		RequestTimeout: durationEnv("WISLA_ARM_REQUEST_TIMEOUT_SEC", 30) * time.Second,
	}
	return cfg, nil
}

func defaultOSType() string {
	switch runtime.GOOS {
	case "linux":
		return "linux"
	case "windows":
		return "windows"
	case "darwin":
		return "macos"
	default:
		return runtime.GOOS
	}
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func durationEnv(key string, fallback int64) time.Duration {
	raw := os.Getenv(key)
	if raw == "" {
		return time.Duration(fallback)
	}
	v, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || v <= 0 {
		return time.Duration(fallback)
	}
	return time.Duration(v)
}
