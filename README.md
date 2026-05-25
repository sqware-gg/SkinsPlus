# SkinsPlus

[![Build](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml)

SkinsPlus is a standalone Paper skin restoration and skin management plugin. It gives players familiar `/skin`, `/skins`, and `/sr reload` workflows while keeping the implementation lightweight and server-owner friendly.

It is designed for Minecraft servers that want simple player skin control without NMS, packet reflection, or a large dependency chain.

## Links

- Website: https://sqware.gg
- Plugin information and support: https://discord.sqware.gg

## Compatibility

- Server software: Paper
- API target: Paper `1.17.1`
- Java: `16+`
- Build tool: Maven
- Server internals: no NMS or packet reflection

As of May 2026, Paper is the supported target. The plugin uses official Paper/Bukkit APIs and Mojang session services.

## Features

- Automatic username skin lookup on join.
- Player commands for setting, clearing, updating, listing, and randomizing skins.
- Fallback skins when a username lookup fails.
- Stable random fallback selection so players keep a consistent fallback.
- Signed Mojang texture caching in `skin-cache.yml`.
- Cache expiry for successful and failed lookups.
- `/skin` and `/skins` aliases for familiar player workflows.
- SkinsRestorer-style compatibility permissions for common setups.
- Config update safety through `config-new.yml`.

## Installation

1. Download the latest SkinsPlus jar from GitHub Releases.
2. Stop your Paper server.
3. Put the jar in your server `plugins` folder.
4. Start the server once to generate `plugins/SkinsPlus/config.yml`.
5. Review fallback skins and cache settings.
6. Restart the server, or run `/sr reload`.

Players do not need setup. On join, SkinsPlus applies the username skin if Mojang has one. If not, it uses the configured fallback behavior.

## Commands

```text
/skin set <name>
/skin auto
/skin clear
/skin none
/skin update
/skin status
/skin list
/skin info <name>
/skin random
```

Aliases:

```text
/skins
/setskin
```

Admin:

```text
/sr reload
```

## Permissions

```text
skinsplus.command.skin       - use player skin commands, default true
skinsplus.admin              - use /sr reload, default op
skinsrestorer.command.set    - compatibility permission for /skin set, default true
skinsrestorer.command.clear  - compatibility permission for clear/none, default true
skinsrestorer.command.update - compatibility permission for update, default true
skinsrestorer.admin          - admin compatibility permission, default op
skinsrestorer.admincommand   - admin compatibility permission, default op
```

## Configuration

```yaml
auto-name-lookup: true

fallback-skins:
  enabled: true
  selection: "stable-random"
  list:
    - Steve
    - Alex
    - Notch
    - jeb_
    - Dinnerbone

login-lookup-timeout-seconds: 4
skin-cache-ttl-hours: 24
missing-profile-cache-ttl-minutes: 15
reapply-on-reload: true
```

Fallback selection modes:

- `stable-random`: each player gets a consistent fallback from the list.
- `random`: picks a fallback each time.
- `first`: always uses the first fallback skin.

## Cache Behavior

SkinsPlus stores Mojang's signed `textures` value and signature in `plugins/SkinsPlus/skin-cache.yml`. It does not save skin image files.

Caching reduces Mojang API calls, makes joins more reliable during temporary Mojang API issues, and keeps login lookup time bounded.

## Updating

SkinsPlus does not overwrite your existing `config.yml`. If the bundled config changes, the plugin writes `plugins/SkinsPlus/config-new.yml` so you can compare and copy new settings.

Keep `skin-cache.yml` between updates unless you intentionally want to force fresh Mojang lookups.

## Build From Source

```powershell
./mvnw.cmd package
```

The shaded server jar is written to:

```text
target/SkinsPlus-0.1.0-SNAPSHOT.jar
```

## Troubleshooting

- If skins do not update immediately for every viewer, have the player relog after changing skin.
- If Mojang lookups fail, check outbound HTTPS access to Mojang API and sessionserver endpoints.
- If fallback skins are all the same, change `fallback-skins.selection`.
- If players cannot use commands, check both `skinsplus.*` and compatibility `skinsrestorer.*` permissions.

## Support

For setup help, compatibility questions, and plugin information, use https://discord.sqware.gg.
