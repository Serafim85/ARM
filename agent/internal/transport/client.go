package transport

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

const agentKeyHeader = "X-Agent-Key"

type Client struct {
	baseURL    string
	agentKey   string
	httpClient *http.Client
}

func NewClient(baseURL, agentKey string, timeout time.Duration) *Client {
	return &Client{
		baseURL:  strings.TrimRight(baseURL, "/"),
		agentKey: agentKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

func (c *Client) PostIngest(ctx context.Context, batch IngestBatch) error {
	payload, err := json.Marshal(batch)
	if err != nil {
		return fmt.Errorf("marshal batch: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/api/v1/agent/ingest", bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set(agentKeyHeader, c.agentKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("post ingest: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("ingest status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	return nil
}
