-- Run using psql as root; shared compose initializes these same credentials.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'videoadmin') THEN
        CREATE ROLE videoadmin LOGIN PASSWORD 'videoadmin';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'theuser') THEN
        CREATE ROLE theuser LOGIN PASSWORD 'theuser';
    END IF;
END $$;

SELECT 'CREATE DATABASE videodb OWNER videoadmin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'videodb')\gexec

\connect videodb
GRANT CONNECT ON DATABASE videodb TO theuser;
GRANT USAGE ON SCHEMA public TO theuser;
ALTER DEFAULT PRIVILEGES FOR ROLE videoadmin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO theuser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO theuser;
