-- Show and set the default transaction isolation level.
--
-- READ COMMITTED is already the PostgreSQL default; this makes the application's
-- requirement explicit and survives restarts (ALTER DATABASE persists it).

SHOW default_transaction_isolation;

ALTER DATABASE vf SET default_transaction_isolation = 'read committed';
