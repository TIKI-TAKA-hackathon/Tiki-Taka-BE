package main

import (
	"flag"
	"fmt"
	"log"
	"net/http"
	"strings"
	"time"
)

func main() {
	baseURL := flag.String("base-url", "", "API base URL, for example https://api.example.com")
	flag.Parse()

	if strings.TrimSpace(*baseURL) == "" {
		log.Fatal("--base-url is required")
	}

	client := &http.Client{Timeout: 10 * time.Second}
	for _, path := range []string{"/actuator/health", "/api/v1/topics"} {
		if err := check(client, *baseURL, path); err != nil {
			log.Fatal(err)
		}
	}

	fmt.Println("smokecheck passed")
}

func check(client *http.Client, baseURL string, path string) error {
	url := strings.TrimRight(baseURL, "/") + path
	resp, err := client.Get(url)
	if err != nil {
		return fmt.Errorf("GET %s failed: %w", url, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("GET %s returned non-2xx status: %d", url, resp.StatusCode)
	}

	fmt.Printf("GET %s -> %d\n", url, resp.StatusCode)
	return nil
}
