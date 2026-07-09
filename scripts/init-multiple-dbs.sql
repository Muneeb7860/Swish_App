-- Initialize multiple databases for system separation
CREATE DATABASE keycloak_db;
CREATE DATABASE letta;

-- Letta stores its passages/embeddings via pgvector; the extension is
-- per-database, so it must be created here, not on the default database.
\connect letta
CREATE EXTENSION IF NOT EXISTS vector;
