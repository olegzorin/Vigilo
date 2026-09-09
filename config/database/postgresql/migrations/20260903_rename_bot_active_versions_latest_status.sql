BEGIN;

DROP INDEX ui_bot_active_versions_latest;

ALTER TABLE bot_active_versions
    RENAME COLUMN is_latest TO latest_status;

ALTER TABLE bot_active_versions
    RENAME CONSTRAINT chk_public_latest TO chk_public_latest_status;

ALTER TABLE bot_active_versions
    ADD CONSTRAINT uk_bot_active_versions_latest_status
        UNIQUE (bot_id, latest_status);

COMMIT;
