-- Breaking VF domain rename: bot -> Lambda.
-- Apply all earlier required migrations first. Stop event ingress and schedules, drain work
-- with the old workers, then stop every VF process. See docs/lambda-rename-upgrade.md.
-- Run against the intended database with psql -v ON_ERROR_STOP=1 -f this-file.sql.
-- Metadata-only renames preserve IDs, data, identity ownership and foreign-key relationships.
-- ACCESS EXCLUSIVE locks are required; the timeout rolls back rather than waiting indefinitely.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path = public, pg_catalog;

DO $$
DECLARE
    obj record;
    current_name text;
BEGIN
    IF to_regclass('public.bots') IS NULL OR to_regclass('public.lambdas') IS NOT NULL THEN
        RAISE EXCEPTION 'Expected pre-rename bots schema and no lambdas table';
    END IF;
    IF to_regclass('public.bot_pending_inputs_p') IS NULL OR NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'public.bot_active_versions'::regclass
          AND attname = 'latest_status' AND NOT attisdropped
    ) THEN
        RAISE EXCEPTION 'Apply the earlier pending-input and latest-status migrations first';
    END IF;

    -- Freeze tables before checking the drain requirement. The public schema is owned by VF.
    FOR obj IN
        SELECT c.oid, c.relname FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
        ORDER BY c.oid
    LOOP
        EXECUTE format('LOCK TABLE public.%I IN ACCESS EXCLUSIVE MODE', obj.relname);
    END LOOP;

    -- Serialized work uses the old API fields, headers and cache names. Never silently discard it.
    IF EXISTS (SELECT 1 FROM bot_latest_runs WHERE run_id > 0 OR pending_count > 0)
       OR EXISTS (SELECT 1 FROM bot_invoke_retry_outbox)
       OR EXISTS (SELECT 1 FROM bot_async_submission_outbox)
       OR EXISTS (SELECT 1 FROM bot_run_completion_outbox)
       OR EXISTS (SELECT 1 FROM bot_reset_outbox)
       OR EXISTS (SELECT 1 FROM cache_invalidation_outbox)
       OR EXISTS (SELECT 1 FROM bot_code_uploads WHERE status IN ('CREATED', 'IN_PROGRESS')) THEN
        RAISE EXCEPTION 'Drain active runs, pending work, deployments and outboxes with the old VF release before renaming';
    END IF;

    -- Rename only locally defined columns; PostgreSQL propagates parent changes to partitions.
    FOR obj IN
        SELECT c.relname, a.attname FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND a.attnum > 0 AND NOT a.attisdropped AND a.attinhcount = 0
          AND a.attname LIKE 'bot\_%' ESCAPE '\'
        ORDER BY c.oid, a.attnum
    LOOP
        EXECUTE format('ALTER TABLE public.%I RENAME COLUMN %I TO %I',
            obj.relname, obj.attname, replace(obj.attname, 'bot', 'lambda'));
    END LOOP;

    FOR obj IN
        SELECT c.relname FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND (c.relname = 'bots' OR c.relname LIKE 'bot\_%' ESCAPE '\')
        ORDER BY c.oid
    LOOP
        EXECUTE format('ALTER TABLE public.%I RENAME TO %I',
            obj.relname, replace(obj.relname, 'bot', 'lambda'));
    END LOOP;

    -- Renaming a unique/primary-key constraint also renames its supporting index.
    FOR obj IN
        SELECT c.relname, con.oid FROM pg_constraint con
        JOIN pg_class c ON c.oid = con.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND position('bot' IN con.conname) > 0
        ORDER BY con.oid
    LOOP
        -- A parent rename can propagate to inherited constraints (including PostgreSQL 18
        -- NOT NULL constraints). Read the current name instead of the cursor's snapshot.
        SELECT conname INTO current_name FROM pg_constraint WHERE oid = obj.oid;
        IF position('bot' IN current_name) > 0 THEN
            EXECUTE format('ALTER TABLE public.%I RENAME CONSTRAINT %I TO %I',
                obj.relname, current_name, replace(current_name, 'bot', 'lambda'));
        END IF;
    END LOOP;

    -- Includes partition indexes and identity sequences, with ownership preserved by OID.
    FOR obj IN
        SELECT c.relname, c.relkind FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('i', 'I', 'S')
          AND position('bot' IN c.relname) > 0
        ORDER BY c.oid
    LOOP
        EXECUTE format('ALTER %s public.%I RENAME TO %I',
            CASE WHEN obj.relkind = 'S' THEN 'SEQUENCE' ELSE 'INDEX' END,
            obj.relname, replace(obj.relname, 'bot', 'lambda'));
    END LOOP;
END
$$;
COMMIT;
