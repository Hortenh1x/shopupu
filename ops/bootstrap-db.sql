\set ON_ERROR_STOP on
-- Run as the database administrator against an explicitly chosen, empty database.
-- Existing databases require the migration/ownership procedure in docs/database-recovery.md.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')) THEN
        RAISE EXCEPTION 'Bootstrap requires an empty database; follow the existing-database ownership procedure';
    END IF;
END $$;
SELECT 'CREATE ROLE shopupu_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shopupu_migrator') \gexec
SELECT 'CREATE ROLE shopupu_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shopupu_runtime') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO shopupu_migrator;
CREATE EXTENSION IF NOT EXISTS vector;
GRANT USAGE ON SCHEMA public TO shopupu_runtime;
SELECT format('GRANT CONNECT ON DATABASE %I TO shopupu_migrator, shopupu_runtime', current_database()) \gexec
-- Set different passwords interactively with psql \password, then enable LOGIN.
