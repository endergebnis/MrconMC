-- Schema fuer SQLite. Fremdschluessel brauchen PRAGMA foreign_keys=ON (siehe JDBC-URL in application.yml);
-- die Services loeschen abhaengige Zeilen trotzdem explizit.

CREATE TABLE guild_config (
    guild_id             INTEGER PRIMARY KEY,
    channel_id           INTEGER,
    message_id           INTEGER,
    admin_role_id        INTEGER,
    registration_message TEXT,
    whitelist_status     TEXT NOT NULL DEFAULT 'CLOSED'
);

CREATE TABLE minecraft_server (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   INTEGER   NOT NULL REFERENCES guild_config (guild_id) ON DELETE CASCADE,
    name       TEXT      NOT NULL,
    type       TEXT      NOT NULL,
    host       TEXT      NOT NULL,
    port       INTEGER   NOT NULL,
    secret_enc TEXT      NOT NULL,
    enabled    INTEGER   NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX ux_minecraft_server_guild_name ON minecraft_server (guild_id, name COLLATE NOCASE);

CREATE TABLE player (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id         INTEGER   NOT NULL,
    discord_id       TEXT      NOT NULL,
    discord_username TEXT      NOT NULL,
    mc_username      TEXT      NOT NULL,
    mc_uuid          TEXT      NOT NULL,
    platform         TEXT      NOT NULL DEFAULT 'java',
    registered_at    TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX ux_player_guild_discord ON player (guild_id, discord_id);
CREATE UNIQUE INDEX ux_player_guild_uuid ON player (guild_id, mc_uuid);

CREATE TABLE whitelist_entry (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER   NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    server_id INTEGER   NOT NULL REFERENCES minecraft_server (id) ON DELETE CASCADE,
    added_at  TIMESTAMP NOT NULL,
    UNIQUE (player_id, server_id)
);
