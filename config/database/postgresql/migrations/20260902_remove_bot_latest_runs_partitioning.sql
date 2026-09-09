-- Rebuilds bot_latest_runs as one physical table while preserving its rows.
-- The exclusive lock prevents writes during the copy, so schedule this migration
-- for a maintenance window.

BEGIN;

LOCK TABLE bot_latest_runs IN ACCESS EXCLUSIVE MODE;

CREATE TABLE bot_latest_runs_unpartitioned
(
    LIKE bot_latest_runs
        INCLUDING DEFAULTS
        INCLUDING CONSTRAINTS
        INCLUDING GENERATED
        INCLUDING IDENTITY
        INCLUDING STORAGE
        INCLUDING COMMENTS
);

INSERT INTO bot_latest_runs_unpartitioned
SELECT * FROM bot_latest_runs;

DROP TABLE bot_latest_runs;

ALTER TABLE bot_latest_runs_unpartitioned
    RENAME TO bot_latest_runs;

ALTER TABLE bot_latest_runs
    ADD CONSTRAINT bot_latest_runs_pkey
        PRIMARY KEY (bot_assignment_id, invocation_lane);

CREATE INDEX i_bot_latest_runs_aws_request_id ON bot_latest_runs(aws_request_id);
CREATE INDEX i_bot_latest_runs_expiry ON bot_latest_runs(invocation_lane, expiry_date);

COMMIT;
