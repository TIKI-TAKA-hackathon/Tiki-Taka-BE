CREATE TABLE topics (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE question_templates (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    display_text TEXT NOT NULL,
    tts_text TEXT NOT NULL,
    audio_file_name VARCHAR(255),
    read_speed NUMERIC(3,2) DEFAULT 0.85,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE memory_sessions (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    interviewer_name VARCHAR(50),
    elder_name VARCHAR(50),
    status VARCHAR(30) DEFAULT 'DRAFT',
    visibility VARCHAR(30) DEFAULT 'PRIVATE',
    share_token VARCHAR(100) UNIQUE NOT NULL,
    consent_checked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE memory_answers (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES memory_sessions(id),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    youth_reply TEXT,
    emotion_tag VARCHAR(50),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE memory_cards (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES memory_sessions(id),
    card_title VARCHAR(150) NOT NULL,
    one_line_summary TEXT,
    elder_quote TEXT,
    youth_reply TEXT,
    tags TEXT,
    template_key VARCHAR(50) DEFAULT 'BASIC',
    share_token VARCHAR(100) UNIQUE NOT NULL,
    is_published BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
