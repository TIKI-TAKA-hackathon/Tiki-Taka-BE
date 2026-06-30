INSERT INTO topics (title, description, sort_order)
VALUES
    ('첫 월급', '처음 돈을 벌었을 때의 기억', 1),
    ('인생 노래', '노래와 함께 떠오르는 시절의 기억', 2),
    ('그때 그 동네', '예전 동네와 장소에 얽힌 기억', 3);

INSERT INTO question_templates (
    topic_id,
    display_text,
    tts_text,
    audio_file_name,
    read_speed,
    sort_order
)
VALUES
    (
        (SELECT id FROM topics WHERE title = '첫 월급'),
        '첫 월급으로 가장 먼저 산 것은 무엇인가요?',
        '처음 월급을 받으셨을 때, 제일 먼저 사고 싶었던 것이 있으셨나요? 천천히 떠올려 보셔도 괜찮습니다.',
        'first_salary_01_v1.mp3',
        0.85,
        1
    ),
    (
        (SELECT id FROM topics WHERE title = '첫 월급'),
        '첫 월급을 받았을 때 가장 먼저 떠오른 사람은 누구였나요?',
        '첫 월급을 손에 쥐었을 때, 가장 먼저 생각난 사람은 누구였나요?',
        'first_salary_02_v1.mp3',
        0.85,
        2
    ),
    (
        (SELECT id FROM topics WHERE title = '인생 노래'),
        '들으면 예전 기억이 떠오르는 노래가 있나요?',
        '들으면 그때의 장면이 떠오르는 노래가 있으신가요?',
        'life_song_01_v1.mp3',
        0.85,
        1
    ),
    (
        (SELECT id FROM topics WHERE title = '인생 노래'),
        '그 노래를 누구와 들으셨나요?',
        '그 노래를 들을 때 함께 있던 사람이나 떠오르는 사람이 있으신가요?',
        'life_song_02_v1.mp3',
        0.85,
        2
    ),
    (
        (SELECT id FROM topics WHERE title = '그때 그 동네'),
        '예전 동네에서 가장 기억나는 장소는 어디인가요?',
        '예전에 사시던 동네에서 지금도 또렷하게 기억나는 장소가 있으신가요?',
        'old_town_01_v1.mp3',
        0.85,
        1
    ),
    (
        (SELECT id FROM topics WHERE title = '그때 그 동네'),
        '그 장소에서 어떤 일이 있었나요?',
        '그 장소에서 있었던 일 중에 아직도 마음에 남아 있는 순간이 있으신가요?',
        'old_town_02_v1.mp3',
        0.85,
        2
    );
