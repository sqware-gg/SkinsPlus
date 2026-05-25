# SkinsPlus

[![Build](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml/badge.svg)](https://github.com/sqware-gg/SkinsPlus/actions/workflows/build.yml)

SkinsPlus is a Minecraft skin plugin for Paper servers. It restores player skins, lets players change or clear their skin with simple commands, caches signed Mojang texture data, and provides fallback skins when a username lookup is missing or unavailable.

Use it for survival servers, cracked/offline-mode networks, events, lobby servers, roleplay servers, or any Paper server where players expect reliable `/skin` commands and automatic skin handling.

## Links

- Website: https://sqware.gg
- Support and plugin updates: https://discord.sqware.gg

## Compatibility

- Server software: Paper
- API target: Paper `1.17.1`
- Java: `16+`
- Build tool: Maven
- Server internals: no NMS or packet reflection

As of May 2026, Paper is the supported target. The plugin uses official Paper/Bukkit APIs and Mojang session services.

## Why Server Owners Use It

- Restore username skins automatically when players join.
- Give players simple commands to set, clear, update, inspect, or randomize skins.
- Cache signed Mojang skin data to reduce API calls and improve join reliability.
- Use fallback skins for players without a matching Mojang profile.
- Avoid NMS and packet-reflection maintenance problems.

## Features

- Automatic username skin lookup on join.
- `/skin` and `/skins` player commands.
- Set skin by Minecraft username.
- Clear, disable, update, list, inspect, and randomize skin choices.
- Configurable fallback skins.
- Stable random fallback mode so players keep a consistent fallback.
- Signed texture caching in `skin-cache.yml`.
- Cache expiry for successful and failed lookups.
- Compatibility permission nodes for common existing permission setups.
- Config-safe updates through `config-new.yml`.

## Installation

1. Download the latest jar from the GitHub Releases page.
2. Stop your Paper server.
3. Put the jar in the server `plugins` folder.
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
```

SkinsPlus also accepts several legacy compatibility permission nodes so existing server permission setups can migrate without immediately rewriting every group.

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

Release history is tracked in [CHANGELOG.md](CHANGELOG.md).

## Build From Source

```powershell
./mvnw.cmd package
```

The shaded server jar is written to:

```text
target/SkinsPlus-0.1.0.jar
```

## Troubleshooting

- Skin does not update immediately for every viewer: have the player relog after changing skin.
- Mojang lookups fail: check outbound HTTPS access to Mojang API and sessionserver endpoints.
- Fallback skins are all the same: change `fallback-skins.selection`.
- Players cannot use commands: check the `skinsplus.*` permission nodes and any compatibility permissions your server already grants.

## License

SkinsPlus is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Support

For setup help, compatibility questions, and plugin updates, use https://discord.sqware.gg.
