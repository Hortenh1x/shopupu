\set ON_ERROR_STOP on
-- Run as schema owner after every verified migration. Runtime never receives DDL ownership.
GRANT USAGE ON SCHEMA public TO shopupu_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO shopupu_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO shopupu_runtime;
REVOKE ALL ON TABLE public.flyway_schema_history FROM shopupu_runtime;
GRANT SELECT ON TABLE public.flyway_schema_history TO shopupu_runtime;
