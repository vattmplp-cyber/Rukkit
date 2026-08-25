[中文](README_zh.md)

# Rukkit Project
![RukkitLogo](rukkit.png)
[![](https://img.shields.io/badge/QQ群-751977820-red.svg)]({linkUrl})
[![](https://img.shields.io/badge/Discord-link-purple.svg)](https://discord.gg/JJJ6GST)
## What is Rukkit?
Rukkit is a Rustedwarfare Server,you can use it to run a private server on your vps or other devices.
It's more like Dedicated server but more features like custom maps or mods, extra plugins.
This project based on netty framework so it as stable as Dedicated Servers.

## Progress
- [x] Basic Game Play
- [x] Custom maps
- [x] Game sync & rejoin (support offical maps and custom too)
- [x] Basic Plugin System
- [x] Mod server by mod's metadata (need a exportTool)
- [x] 10p+ Multiplayer supported (only 1.14+)
- [ ] No-stop game future.
- [ ] Game hook(Events) supported some event not all.
- [ ] Anti-Cheat sync (need a game Simulation layer)
- [ ] Advanced gameCommand & save modification. (it's hard to make changes to them,the most result is crash the game.)
- [ ] Relay mode (maybe in future version)

## About
Some plugin system design referenced [Nukkit](https://github.com/Nukkit/Nukkit).

## Unstable warning
this is still a unstable build.If you find bugs,please commit issues.
if you fixed some bugs, you can have a PR.

## Multi-server console management

On Windows, `server start <name>` opens a separate CMD console for the child Rukkit server by default. Use `server start <name> background` to keep it in the background and write output to `server.log`.

Commands:

- `server create <name> [port]`
- `server start <name> [console|background]`
- `server stop <name>`
- `server restart <name>`
- `server list`
- `server info <name>`

Each child server has its own working directory and `rukkit.yml`, but can share the same Rukkit JAR, plugins, maps, and mods via absolute configured paths. To run commands such as `publish start` for a child server, use that server's own CMD console.

## Multi-server manager

The custom build can manage up to `serverManagerMaxServers` child Rukkit instances from the parent console.

Examples:

```text
server create server2 5124
server start server2
server list
server info server2
server select 3-8
server send selected publish start
server broadcast publish stop
server stop 3-8
server restart all
server delete server2
```

Targets support `all`, `selected`, a server name, a numeric list index, ranges such as `3-8`, and comma-separated selections such as `2,5,7`.

Child servers use a localhost control port and token, so commands can be sent from the parent manager even when each child has its own Windows CMD window.

## Notification overrides

`rukkit.yml` supports a `notifications:` map. Keys such as `rukkit.playerJoin`, `rukkit.playerLeft`, `rukkit.playerReconnect`, `rukkit.gameFull`, and `rukkit.gameStarted` can be overridden without rebuilding. Supported placeholders include `{serverName}`, `{serverPort}`, `{roomId}`, `{playerName}`, `{reason}`, and `{fileName}`.
