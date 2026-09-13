\set ON_ERROR_STOP on
SELECT current_database(), current_user;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT datcollversion AS recorded, pg_database_collation_actual_version(oid) AS actual
FROM pg_database WHERE datname = current_database();
SELECT count(*) AS invalid_inventory FROM inventory WHERE stock < 0 OR reserved < 0 OR reserved > stock;
SELECT count(*) AS users FROM users;
SELECT count(*) AS orders FROM orders;
SELECT count(*) AS payments FROM payments;
SELECT count(*) AS reviews FROM reviews;
