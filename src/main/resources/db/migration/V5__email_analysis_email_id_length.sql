-- Provider message IDs (e.g. Graph, IMAP) and composite identifiers can exceed VARCHAR(255).

ALTER TABLE email_analysis
    MODIFY COLUMN email_id VARCHAR(2048) NULL;
