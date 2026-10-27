# RconMC (Multi-Guild)

Discord-Bot (Spring Boot + JDA), über den Nutzer ihren Minecraft-Namen registrieren und automatisch auf die
Whitelist der Minecraft-Server ihrer Discord-Guild gesetzt werden. **Eine Bot-Instanz** bedient mehrere Guilds;
jede Guild verwaltet ihre eigenen Minecraft-Server.

## Module (Maven)

| Modul | Inhalt | Abhängigkeiten |
| --- | --- | --- |
| `core` | Ports (`MinecraftServerGateway`, `AccountVerifier`) und Ergebnistypen | – |
| `crypto` | `SecretCipher` (AES-256-GCM) | nur JDK |
| `rcon` | `RconClient`, `RconServerGateway` (ein Client je Server-ID) | core, slf4j |
| `verify` | `MojangVerifier` | core, Spring Web |
| `persistence` | JPA-Entities, Repositories, Flyway-Migrationen (SQLite) | core |
| `whitelist` | `GuildConfigService`, `ServerService`, `WhitelistService`, `HostPolicy` | core, crypto, persistence |
| `discord` | JDA-Gateway, Commands, Buttons, Modals, Listener | whitelist |
| `app` | `RconMcApplication`, Konfiguration, `rconmc.jar` | alle |

Ein zweites Gateway (z. B. Management Protocol, `ServerType.MGMT_WS`) ist ein neues Modul, das
`MinecraftServerGateway` implementiert und in `app` als Bean registriert wird; Fachlogik und Discord-Schicht
ändern sich nicht.

## Setup pro Guild

1. Bot einladen, dann als Admin (Recht „Server verwalten“) `/setup channel:#whitelist [admin-role:@Rolle] [message:Text]`.
   Das postet die Registrierungsnachricht und speichert Kanal, Nachricht und Admin-Rolle für diese Guild.
2. `/server add` öffnet ein Formular (Name, Host, RCON-Port, RCON-Passwort). Die Verbindung wird getestet, erst dann
   wird gespeichert. Das Passwort wird AES-256-GCM-verschlüsselt abgelegt.
3. `/whitelist open` öffnet die Whitelist; Registrierungen landen sofort auf allen aktiven Servern der Guild.

## Befehle

| Command | Wer | Beschreibung |
| --- | --- | --- |
| `/setup` | „Server verwalten“ | Registrierungsnachricht einrichten / verschieben |
| `/server add \| remove \| list \| test` | Guild-Admin | Eigene Minecraft-Server verwalten |
| `/whitelist open \| close` | Guild-Admin | Whitelist der Guild öffnen/schließen (öffnen trägt fehlende Spieler nach) |
| `/whitelist open-all \| close-all` | Guild-Admin | Alle Spieler auf alle Server setzen / entfernen |
| `/whitelist user \| dcuser` | Guild-Admin | Minecraft- ↔ Discord-Name nachschlagen |

Mandantentrennung: Jeder Service-Aufruf trägt die `guildId` der aufrufenden Guild, alle Abfragen filtern danach.
Ein MC-Account gehört pro Guild höchstens einem Discord-Nutzer.

## Konfiguration

| Variable | Beschreibung |
| --- | --- |
| `DISCORD_TOKEN` | Bot-Token (nur Env/Docker-Secret) |
| `RCONMC_MASTER_KEY` | base64, 32 Byte (`openssl rand -base64 32`). **Sichern** – ohne Key sind die RCON-Passwörter nicht mehr lesbar |
| `DB_PATH` | Pfad der SQLite-Datei (Standard `./data/rconmc.db`) |
| `RCONMC_MAX_SERVERS_PER_GUILD` | Standard `5` |
| `RCONMC_ALLOW_PRIVATE_HOSTS` | Standard `true`. `false` lehnt Hosts in privaten/lokalen Netzen ab |

Alternativ als Docker-Secret über `spring.config.import=optional:configtree:/run/secrets/`
(Dateien `rconmc.discord.token`, `rconmc.security.master-key`).

## Sicherheit

- **AES-256-GCM**, zufällige IV je Wert, Format `v1:<base64>`; die Guild-ID ist als AAD gebunden, ein kopierter
  Ciphertext funktioniert in einer anderen Guild nicht. Schützt vor einer geleakten DB-Datei, nicht vor einem
  kompromittierten Host (dort liegt der Key).
- **Discord-Modals maskieren keine Eingaben**: Das Passwort ist beim Tippen sichtbar. Es steht nie als Command-Option
  im Verlauf, wird nicht geloggt und nicht in Antworten wiederholt.
- **RCON ist unverschlüsselt.** Server außerhalb des eigenen Netzes nur über VPN/SSH-Tunnel anbinden.
- **SSRF:** Guild-Admins tragen Host und Port selbst ein; der Bot verbindet dorthin. Bei `allow-private-hosts=true`
  können sie über Fehlermeldungen interne Adressen des Bot-Hosts sondieren. Sobald fremde Guilds den Bot nutzen:
  `false` setzen und den Egress des Containers per Firewall begrenzen.
- Passwort-Änderung eines Servers: `/server remove` und neu hinzufügen.

## Datenbank

SQLite über Hibernate (`hibernate-community-dialects`), Schema per Flyway (`persistence/.../db/migration`),
`ddl-auto: none`. WAL, `busy_timeout=5000`, Pool-Größe 1. Es gibt keinen Import aus der alten H2-Datenbank
(0.1.x); Spieler müssen sich neu registrieren oder per Skript migriert werden.

## Build

```bash
mvn package
docker compose up -d
```

Java 25, Spring Boot 4.1. Tests: `mvn test`.
