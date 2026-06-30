package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"
)

type questionManifest struct {
	Questions []questionSeed `json:"questions"`
}

type questionSeed struct {
	DisplayText   string  `json:"displayText"`
	TtsText       string  `json:"ttsText"`
	AudioFileName string  `json:"audioFileName"`
	ReadSpeed     float64 `json:"readSpeed"`
}

func main() {
	file := flag.String("file", "", "question seed manifest JSON file")
	flag.Parse()

	if strings.TrimSpace(*file) == "" {
		log.Fatal("--file is required")
	}

	questions, err := readQuestions(*file)
	if err != nil {
		log.Fatal(err)
	}
	if err := validateQuestions(questions); err != nil {
		log.Fatal(err)
	}

	fmt.Printf("validated %d questions\n", len(questions))
}

func readQuestions(file string) ([]questionSeed, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}

	var manifest questionManifest
	if err := json.Unmarshal(data, &manifest); err == nil && manifest.Questions != nil {
		return manifest.Questions, nil
	}

	var questions []questionSeed
	if err := json.Unmarshal(data, &questions); err != nil {
		return nil, err
	}
	return questions, nil
}

func validateQuestions(questions []questionSeed) error {
	if len(questions) == 0 {
		return errors.New("manifest must contain at least one question")
	}

	for i, question := range questions {
		if strings.TrimSpace(question.DisplayText) == "" {
			return fmt.Errorf("questions[%d].displayText must not be empty", i)
		}
		if strings.TrimSpace(question.TtsText) == "" {
			return fmt.Errorf("questions[%d].ttsText must not be empty", i)
		}
		if strings.TrimSpace(question.AudioFileName) == "" {
			return fmt.Errorf("questions[%d].audioFileName must not be empty", i)
		}
		if question.ReadSpeed < 0.5 || question.ReadSpeed > 1.2 {
			return fmt.Errorf("questions[%d].readSpeed must be between 0.5 and 1.2", i)
		}
	}

	return nil
}
