# AE Throughput Monitor (aetm)

Throughput monitoring for **Applied Energistics 2** on **Minecraft 1.19.2 / Forge 43.x**.

## What it adds

| Item | Description |
|---|---|
| **ME Throughput Monitor** | A cable part that watches one item/fluid in the network and shows icon + amount + rate on its face. Rate unit cycles per tick / second / minute / 10 minutes (module the configurator). |
| **Advanced ME Throughput Monitor** | Same part plus an in-GUI config slot, a sample history, and a line chart (storage / flow / acceleration) with selectable time window. |
| **ME Storage Monitor Terminal** | A cable part that lists **every throughput monitor on the same network**, merges monitors that watch the same key into one row, and opens **draggable per-target chart windows** (display slot + 160x64 chart + per-window window/metric/unit buttons + "locate" highlight). |
| **Wireless ME Storage Monitor Terminal** | The same terminal as an AE2 wireless terminal (link it in a Security Terminal, stay within a Wireless Access Point's range). |

Right-click a part to open its GUI; double-click a row in the terminal to open that row's chart window.

## Requirements

- Minecraft 1.19.2, Forge 43.5.0+
- Applied Energistics 2 12.9.9 (required)

## Configuration

`<save>/serverconfig/aetm-server.toml` (hot-reloaded):

- `samplingIntervalTicks` - how often monitors read the network (default `10` = 0.5 s)
- `historyCapacity` - samples kept per monitor (default `3600`)
- `chartRefreshMultiplier` - how often terminal chart windows ask for a fresh curve, as a multiple of the sampling interval (default `1.0`, up to `4.0`)

## Building

```bash
./gradlew build          # jar ends up in build/libs
./gradlew runClient      # dev client
```

## License and attribution

This mod is licensed under **GNU LGPL-3.0** (see `LICENSE`).

The ME throughput monitor parts are **ported to 1.19.2 from
[AdvancedAE](https://github.com/pedroksl/AdvancedAE) by Pedroksl (LGPL-3.0)**, which itself
descends from GTLCore's throughput monitoring. The advanced monitor's sampling history,
the line chart, the terminal and its chart windows are this project's own work.
See `NOTICE.txt` for the full attribution list and for what is derived vs. original.

`LICENSE.txt` and `CREDITS.txt` are the files shipped with the Forge MDK template
(Forge's own license/credits) and are kept as-is.

### Note on authorship

Most of the code in this repository was written by an AI assistant (DeepSeek Harness) from
specifications, review and in-game testing by the repository owner. It was reviewed,
compiled and play-tested by a human before release.
