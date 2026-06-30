package collector

import "os"

// New returns real host collector unless WISLA_ARM_USE_STUB=1 (dev/demo sine waves).
func New() Collector {
	if os.Getenv("WISLA_ARM_USE_STUB") == "1" {
		return Stub{}
	}
	return Real{}
}
