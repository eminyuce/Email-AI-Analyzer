-- Last updated time for list ordering (active first, then newest updated among inactive).

SET
@tbl := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_settings');

SET
@col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_settings'
      AND COLUMN_NAME = 'updated_at');

SET
@stmt := IF(@tbl > 0 AND @col = 0,
    'ALTER TABLE app_settings ADD COLUMN updated_at DATETIME(6) NULL',
    'SELECT 1');

PREPARE migr FROM @stmt;
EXECUTE migr;
DEALLOCATE PREPARE migr;

SET
@stmt2 := IF(@tbl > 0,
    'UPDATE app_settings SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP(6)) WHERE updated_at IS NULL',
    'SELECT 1');

PREPARE migr2 FROM @stmt2;
EXECUTE migr2;
DEALLOCATE PREPARE migr2;

SET
@stmt3 := IF(
    @tbl > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'app_settings'
               AND COLUMN_NAME = 'updated_at') > 0,
    'ALTER TABLE app_settings MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
    'SELECT 1');

PREPARE migr3 FROM @stmt3;
EXECUTE migr3;
DEALLOCATE PREPARE migr3;
