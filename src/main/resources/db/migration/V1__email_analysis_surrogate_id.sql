-- Aligns legacy MySQL schemas with EmailAnalysis.id (surrogate key).
-- Older tables often used email_id as PRIMARY KEY or had no id column; Hibernate then still maps "id".

CREATE TABLE IF NOT EXISTS email_analysis
(
    id
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT,
    email_id
    VARCHAR
(
    255
),
    setting_id BIGINT,
    email_date DATETIME
(
    6
),
    processed_at DATETIME
(
    6
),
    subject VARCHAR
(
    255
),
    sender VARCHAR
(
    255
),
    content TEXT,
    in_reply_to TEXT,
    email_references TEXT,
    criticality_score INT,
    criticality_level VARCHAR
(
    255
),
    breakdown JSON,
    summary TEXT,
    key_risks JSON,
    affected_stakeholders JSON,
    action_needed BOOLEAN,
    recommended_action TEXT,
    estimated_response_time VARCHAR
(
    255
),
    confidence INT,
    PRIMARY KEY
(
    id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

SET
@id_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'email_analysis'
      AND COLUMN_NAME = 'id');

SET
@has_pk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'email_analysis'
      AND CONSTRAINT_TYPE = 'PRIMARY KEY');

SET
@stmt := IF(@id_exists > 0,
    'SELECT 1',
    IF(@has_pk > 0,
        'ALTER TABLE email_analysis DROP PRIMARY KEY, ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (id)',
        'ALTER TABLE email_analysis ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST'));

PREPARE migr FROM @stmt;
EXECUTE migr;
DEALLOCATE PREPARE migr;
