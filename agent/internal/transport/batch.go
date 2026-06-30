package transport

import "time"

type MetricPoint struct {
	Key   string    `json:"key"`
	Value float64   `json:"value"`
	Clock time.Time `json:"clock"`
}

type LogEntry struct {
	Level   string     `json:"level"`
	Message string     `json:"message"`
	Clock   *time.Time `json:"clock,omitempty"`
	Source  string     `json:"source,omitempty"`
}

type EventEntry struct {
	Type      string     `json:"type"`
	Message   string     `json:"message"`
	Clock     *time.Time `json:"clock,omitempty"`
	Severity  string     `json:"severity,omitempty"`
	ErrorCode string     `json:"error_code,omitempty"`
	ErrorText string     `json:"error_text,omitempty"`
	Source    string     `json:"source,omitempty"`
}

type IngestBatch struct {
	Hostname     string        `json:"hostname"`
	Timestamp    time.Time     `json:"timestamp"`
	AgentVersion string        `json:"agent_version"`
	OSType       string        `json:"os_type"`
	PrimaryIP    string        `json:"primary_ip,omitempty"`
	Metrics      []MetricPoint `json:"metrics"`
	Logs         []LogEntry    `json:"logs"`
	Events       []EventEntry  `json:"events"`
}

func NewBatch(hostname, agentVersion, osType, primaryIP string, ts time.Time, metrics []MetricPoint, logs []LogEntry, events []EventEntry) IngestBatch {
	if metrics == nil {
		metrics = []MetricPoint{}
	}
	if logs == nil {
		logs = []LogEntry{}
	}
	if events == nil {
		events = []EventEntry{}
	}
	return IngestBatch{
		Hostname:     hostname,
		Timestamp:    ts,
		AgentVersion: agentVersion,
		OSType:       osType,
		PrimaryIP:    primaryIP,
		Metrics:      metrics,
		Logs:         logs,
		Events:       events,
	}
}
