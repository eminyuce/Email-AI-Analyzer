-- Profile creation time for list ordering (active first, then newest created).
-- If app_settings does not exist yet (first boot), Hibernate ddl-auto will create it including this column.

SET
@tbl := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_settings');

SET
@col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_settings'
      AND COLUMN_NAME = 'created_at');

SET
@stmt := IF(@tbl > 0 AND @col = 0,
    'ALTER TABLE app_settings ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
    'SELECT 1');

PREPARE migr FROM @stmt;
EXECUTE migr;
DEALLOCATE PREPARE migr;
