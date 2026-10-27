-- Freigabeliste fuer die geschlossene Whitelist (siehe WhitelistBypass).
-- Die Fremdschluessel-Zeile guild_config muss existieren; der Service legt sie bei Bedarf an.

CREATE TABLE whitelist_bypass (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   INTEGER   NOT NULL REFERENCES guild_config (guild_id) ON DELETE CASCADE,
    discord_id TEXT      NOT NULL,
    granted_by TEXT      NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    UNIQUE (guild_id, discord_id)
);
