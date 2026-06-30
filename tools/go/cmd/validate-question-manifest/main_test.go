package main

import "testing"

func TestValidateQuestionsAcceptsValidManifest(t *testing.T) {
	questions := []questionSeed{
		{
			DisplayText:   "첫 월급으로 가장 먼저 산 것은 무엇인가요?",
			TtsText:       "처음 월급을 받으셨을 때를 떠올려 주세요.",
			AudioFileName: "first_salary_01_v1.mp3",
			ReadSpeed:     0.85,
		},
	}

	if err := validateQuestions(questions); err != nil {
		t.Fatalf("expected valid manifest, got %v", err)
	}
}

func TestValidateQuestionsRejectsMissingRequiredFields(t *testing.T) {
	questions := []questionSeed{
		{
			TtsText:       "질문입니다.",
			AudioFileName: "missing_display_text.mp3",
			ReadSpeed:     0.85,
		},
	}

	if err := validateQuestions(questions); err == nil {
		t.Fatal("expected missing displayText to fail")
	}
}

func TestValidateQuestionsRejectsReadSpeedOutOfRange(t *testing.T) {
	questions := []questionSeed{
		{
			DisplayText:   "질문입니다.",
			TtsText:       "천천히 답해주세요.",
			AudioFileName: "question.mp3",
			ReadSpeed:     1.5,
		},
	}

	if err := validateQuestions(questions); err == nil {
		t.Fatal("expected readSpeed out of range to fail")
	}
}
