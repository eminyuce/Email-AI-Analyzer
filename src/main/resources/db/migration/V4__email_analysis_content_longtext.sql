-- MySQL TEXT (~64 KiB) is too small for large email bodies. LONGTEXT supports up to ~4 GiB.

ALTER TABLE email_analysis
    MODIFY COLUMN content LONGTEXT NULL;
