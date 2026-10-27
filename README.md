# RconMC (Multi-Guild)

Discord-Bot (Spring Boot + JDA), über den Nutzer ihren Minecraft-Namen registrieren und automatisch auf die
Whitelist der Minecraft-Server ihrer Discord-Guild gesetzt werden. **Eine Bot-Instanz** bedient mehrere Guilds;
jede Guild verwaltet ihre eigenen Minecraft-Server.

## Projektstruktur

```
rconmc/
├── pom.xml                  Parent (Versionen, Modulliste)
├── app/                     Startbares Spring-Boot-Artefakt (rconmc.jar)
└── modules/
    ├── core/                Ports (MinecraftServerGateway, AccountVerifier) und Ergebnistypen
    ├── crypto/              SecretCipher (AES-256-GCM), nur JDK
    ├── persistence/         JPA-Entities, Repositories, Flyway-Migrationen (SQLite)
    ├── whitelist/           Fachlogik, gegliedert in Unterpakete (siehe unten)
    └── adapters/            Alles, was mit der Außenwelt spricht
        ├── rcon/            RconClient, RconServerGateway
        ├── verify/          MojangVerifier
        └── discord/         JDA-Gateway, Commands, Buttons, Modals, Listener
```

| Modul | Abhängigkeiten |
| --- | --- |
| `core`, `crypto` | keine (nur JDK) |
| `persistence` | core |
| `whitelist` | core, crypto, persistence |
| `adapters/rcon` | core, slf4j |
| `adapters/verify` | core, Spring Web |
| `adapters/discord` | whitelist |
| `app` | alle Adapter |

Pakete in `persistence` (je Aggregat Entity + Repository zusammen):

| Paket | Inhalt |
| --- | --- |
| `persistence.guild` | `GuildConfig`, `GuildConfigRepository` |
| `persistence.server` | `MinecraftServer`, `MinecraftServerRepository` |
| `persistence.player` | `Player`, `PlayerRepository` |
| `persistence.whitelist` | `WhitelistEntry` (wo der Bot einen Spieler eingetragen hat), `WhitelistBypass` (Freigaben) samt Repositories |

Pakete in `whitelist` (Abhängigkeiten laufen nur nach unten, keine Zyklen):

| Paket | Inhalt |
| --- | --- |
| `whitelist` | `WhitelistService` (Registrierung, open/close, Abfragen), `PlayerInfo` |
| `whitelist.access` | `AccessService`: Freigabeliste für die geschlossene Whitelist |
| `whitelist.sync` | `WhitelistSynchronizer`: einzige Stelle, die Server-Whitelists ändert und `whitelist_entry` führt |
| `whitelist.server` | `ServerService`, `GatewayRouter`, `HostPolicy`, `ServerLimits` |
| `whitelist.guild` | `GuildConfigService` (Kanal, Nachricht, Admin-Rolle, Status) |

Ein zweites Gateway (z. B. Management Protocol, `ServerType.MGMT_WS`) ist ein neues Modul unter `modules/adapters/`,
das `MinecraftServerGateway` implementiert und in `app` als Bean registriert wird; Fachlogik und Discord-Schicht
ändern sich nicht.

## Setup pro Guild

1. Bot einladen, dann als Admin (Recht „Server verwalten“) `/setup channel:#whitelist [admin-role:@Rolle] [message:Text]`.
   Das postet die Registrierungsnachricht und speichert Kanal, Nachricht und Admin-Rolle für diese Guild.
2. `/server add` öffnet ein Formular (Name, Host, RCON-Port, RCON-Passwort). Die Verbindung wird getestet, erst dann
   wird gespeichert. Das Passwort wird AES-256-GCM-verschlüsselt abgelegt.
3. `/whitelist open` öffnet die Whitelist; Registrierungen landen sofort auf allen aktiven Servern der Guild.
   Standardmäßig ist die Whitelist einer neuen Guild **geschlossen**.

## Befehle

| Command | Wer | Beschreibung |
| --- | --- | --- |
| `/setup` | „Server verwalten“ | Registrierungsnachricht einrichten / verschieben |
| `/server add \| remove \| list \| test` | Guild-Admin | Eigene Minecraft-Server verwalten |
| `/whitelist open` | Guild-Admin | Whitelist öffnen; fehlende Spieler werden nachgetragen |
| `/whitelist close [remove-others]` | Guild-Admin | Whitelist schließen (siehe unten); `remove-others` (Standard `true`) entfernt Spieler ohne Freigabe von den Servern |
| `/whitelist status` | Guild-Admin | Zeigt, ob die Whitelist offen oder geschlossen ist, und wie viele Freigaben es gibt |
| `/whitelist open-all \| close-all` | Guild-Admin | Alle Spieler auf alle Server setzen / entfernen |
| `/whitelist user \| dcuser` | Guild-Admin | Minecraft- ↔ Discord-Name nachschlagen (inkl. Freigabe-Status) |
| `/access add \| remove \| list` | Guild-Admin | Freigaben für die geschlossene Whitelist verwalten |

„Guild-Admin“ = Recht „Server verwalten“ oder die per `/setup` gesetzte Admin-Rolle.

### Geschlossene Whitelist und Freigaben

Eine geschlossene Whitelist heißt: **nur freigegebene Nutzer können joinen.** Die Freigabe hängt an der Discord-ID
(nicht am Minecraft-Namen) und gilt nur in der Guild, in der sie vergeben wurde.

| Situation | Verhalten |
| --- | --- |
| `/whitelist close` | Status wird `CLOSED`. Registrierte Spieler **ohne Freigabe** werden von allen aktiven Servern entfernt, bleiben aber registriert. Mit `remove-others:false` bleiben bereits eingetragene Spieler stehen, nur neue werden gestoppt. |
| Registrierung, Whitelist geschlossen, **keine** Freigabe | Der Spieler wird gespeichert und als *pending* vorgemerkt, nicht eingetragen. |
| Registrierung, Whitelist geschlossen, **mit** Freigabe | Der Spieler wird sofort auf allen aktiven Servern eingetragen. |
| `/access add user:@Nutzer` | Nutzer freigeben. Ist er schon registriert und die Whitelist geschlossen, kommt er sofort auf die Server; sonst beim Registrieren. |
| `/access remove user:@Nutzer` | Freigabe zurücknehmen. Bei geschlossener Whitelist wird der Spieler von den Servern entfernt. |
| `/whitelist open` | Status wird `OPEN`; alle registrierten Spieler, die auf einem Server fehlen, werden nachgetragen (auch die vorher entfernten). |

Grenzen: Der Bot kennt nur Spieler, die sich über ihn registriert haben. Wer manuell (ohne Bot) auf einer
Server-Whitelist steht, wird beim Schließen nicht entfernt. Wechselt ein Spieler seinen Minecraft-Namen per erneuter
Registrierung, wird der alte Name von den aktiven Servern entfernt; ist ein Server dabei offline, bleibt der alte
Name dort bis zur manuellen Bereinigung stehen (steht im Log).

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

SQLite über Hibernate (`hibernate-community-dialects`), Schema per Flyway (`modules/persistence/.../db/migration`, aktuell `V1__init` und `V2__whitelist_bypass`),
`ddl-auto: none`. WAL, `busy_timeout=5000`, Pool-Größe 1. Es gibt keinen Import aus der alten H2-Datenbank
(0.1.x); Spieler müssen sich neu registrieren oder per Skript migriert werden.

## Build

```bash
mvn package
docker compose up -d
```

Java 25, Spring Boot 4.1. Tests: `mvn test`.
