-- Test-only migration: proves that Flyway applies scripts from db/migration/ at startup.
CREATE TABLE core_test_marker (id INTEGER PRIMARY KEY);
