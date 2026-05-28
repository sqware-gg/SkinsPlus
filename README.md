# SkinsPlus

[![Build](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml)

SkinsPlus is a SkinsRestorer-style skin plugin for Paper servers. It restores username skins, lets players change or clear skins with `/skin`, caches signed Mojang texture data, and provides fallback skins for missing profiles.

Use it for offline-mode networks, cracked servers, lobby servers, events, roleplay servers, or any Paper server that needs reliable Minecraft skin commands without NMS or packet reflection.

## Features

- Automatic username skin lookup on join.
- `/skin` and `/skins` player commands.
- Set skin by Minecraft username.
- Clear, disable, update, inspect, list, and randomize skin choices.
- Configurable fallback skins.
- Stable random fallback mode so players keep a consistent fallback.
- Signed Mojang texture caching in `skin-cache.yml`.
- Cache expiry for successful and failed lookups.
- SkinsRestorer-compatible permission nodes for easier migration.
- Config-safe updates through `config-new.yml`.

## Requirements

- Paper
- API target: Paper `1.17.1`
- Java `16+`
- Maven wrapper included
- No NMS or packet reflection

SkinsPlus uses official Paper/Bukkit APIs and Mojang session services.

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
/sr reload
```

Aliases: `/skins`, `/setskin`

## Permissions

```text
skinsplus.command.skin       - use player skin commands, default true
skinsplus.admin              - use /sr reload, default op
skinsrestorer.command.set    - compatibility permission for /skin set
skinsrestorer.command.clear  - compatibility permission for clear/none
skinsrestorer.command.update - compatibility permission for update
skinsrestorer.admin          - compatibility admin permission
skinsrestorer.admincommand   - compatibility admin permission
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

Fallback modes:

- `stable-random`: each player gets a consistent fallback from the list.
- `random`: picks a fallback each time.
- `first`: always uses the first fallback skin.

## Cache Behavior

SkinsPlus stores Mojang's signed `textures` value and signature in `plugins/SkinsPlus/skin-cache.yml`. It does not save skin image files.

Caching reduces Mojang API calls and keeps login lookups bounded during Mojang API issues.

## Build

```powershell
.\mvnw.cmd package
```

The shaded jar is written to `target/SkinsPlus-0.1.0.jar`.

## Support

- Website: https://sqware.gg
- Discord: https://discord.sqware.gg

SkinsPlus is licensed under the Apache License, Version 2.0.
