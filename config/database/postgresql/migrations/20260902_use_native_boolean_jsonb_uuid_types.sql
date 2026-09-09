-- Use PostgreSQL-native types for logical flags, structured JSON, and identifiers
-- that VF itself generates as UUIDs. Invalid non-empty JSON or UUID values stop
-- the migration so legacy data can be corrected rather than silently discarded.

BEGIN;

ALTER TABLE bot_active_versions DROP CONSTRAINT chk_public_latest;
ALTER TABLE bot_active_versions
    ALTER COLUMN is_public TYPE boolean USING is_public <> 0;
ALTER TABLE bot_active_versions
    ADD CONSTRAINT chk_public_latest
        CHECK ((is_public AND is_latest IN (0, 1)) OR
               (NOT is_public AND is_latest IN (1, 2)));

ALTER TABLE bot_assignments
    ALTER COLUMN is_testing TYPE boolean USING is_testing <> 0;
ALTER TABLE bot_latest_runs
    ALTER COLUMN is_started TYPE boolean USING is_started <> 0;
ALTER TABLE bot_input_messages_p
    ALTER COLUMN is_large_message TYPE boolean USING is_large_message <> 0;
ALTER TABLE report_params
    ALTER COLUMN is_required TYPE boolean USING is_required <> 0;
ALTER TABLE report_execution_history ALTER COLUMN on_demand DROP DEFAULT;
ALTER TABLE report_execution_history
    ALTER COLUMN on_demand TYPE boolean USING on_demand <> 0;
ALTER TABLE report_execution_history ALTER COLUMN on_demand SET DEFAULT false;

ALTER TABLE device_current_states
    ALTER COLUMN current_state TYPE jsonb
    USING CASE WHEN btrim(current_state) = '' THEN '{}'::jsonb ELSE current_state::jsonb END;
ALTER TABLE bots
    ALTER COLUMN metadata TYPE jsonb
    USING CASE WHEN metadata IS NULL OR btrim(metadata) = '' THEN NULL ELSE metadata::jsonb END;
ALTER TABLE bot_versions
    ALTER COLUMN schedule TYPE jsonb
    USING CASE WHEN schedule IS NULL OR btrim(schedule) = '' THEN NULL ELSE schedule::jsonb END;
ALTER TABLE bot_active_versions
    ALTER COLUMN schedule TYPE jsonb
    USING CASE WHEN schedule IS NULL OR btrim(schedule) = '' THEN NULL ELSE schedule::jsonb END;
ALTER TABLE bot_alerts
    ALTER COLUMN changes_json TYPE jsonb USING changes_json::jsonb;
ALTER TABLE report_execution_history
    ALTER COLUMN metadata TYPE jsonb
    USING CASE WHEN metadata IS NULL OR btrim(metadata) = '' THEN NULL ELSE metadata::jsonb END;
ALTER TABLE report_group_schedules ALTER COLUMN parameters DROP DEFAULT;
ALTER TABLE report_group_schedules
    ALTER COLUMN parameters TYPE jsonb
    USING CASE WHEN btrim(parameters) = '' THEN '{}'::jsonb ELSE parameters::jsonb END;
ALTER TABLE report_group_schedules ALTER COLUMN parameters SET DEFAULT '{}'::jsonb;

ALTER TABLE bot_alerts
    ALTER COLUMN alert_id TYPE uuid USING alert_id::uuid;
ALTER TABLE cron_job_locks
    ALTER COLUMN owner_id TYPE uuid USING owner_id::uuid;
ALTER TABLE bot_invoke_retry_outbox
    ALTER COLUMN claim_id TYPE uuid USING claim_id::uuid;
ALTER TABLE bot_async_submission_outbox
    ALTER COLUMN claim_id TYPE uuid USING claim_id::uuid;
ALTER TABLE bot_run_completion_outbox
    ALTER COLUMN claim_id TYPE uuid USING claim_id::uuid;
ALTER TABLE bot_reset_outbox
    ALTER COLUMN event_id TYPE uuid USING event_id::uuid,
    ALTER COLUMN claim_id TYPE uuid USING claim_id::uuid;
ALTER TABLE cache_invalidation_outbox
    ALTER COLUMN claim_id TYPE uuid USING claim_id::uuid;

COMMIT;
