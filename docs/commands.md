# Commands and permissions

Every command, permission and config key in CuriousJumpPads. New to the plugin? Start with
[quick-start.md](quick-start.md).

## Commands

| Command | Permission | Does |
|---|---|---|
| [`/launch <targets> set <x> <y> <z>`](#launch) | `jumppads.launch` | Replace velocity |
| [`/launch <targets> add <x> <y> <z>`](#launch) | `jumppads.launch` | Add to velocity |
| [`/launch <targets> forward <power> [lift]`](#launch) | `jumppads.launch` | Launch along the look direction |
| [`/launch <targets> to <x> <y> <z> [ticks]`](#launch) | `jumppads.launch` | Land on a block |
| [`/launch <targets> toward <entity> [ticks]`](#launch) | `jumppads.launch` | Land on an entity |
| [`/jumppad set <launch>`](#jumppad) | `jumppads.admin` | Make the looked-at block a pad |
| [`/jumppad remove`](#jumppad) | `jumppads.admin` | Forget the looked-at pad |
| [`/jumppad info`](#jumppad) | `jumppads.admin` | Show what the looked-at pad does |
| [`/jumppad list`](#jumppad) | `jumppads.admin` | Every pad |
| [`/jumppad enable` \| `disable`](#jumppad) | `jumppads.admin` | Switch the looked-at pad |
| [`/jumppad enable all` \| `disable all`](#jumppad) | `jumppads.admin` | Switch every pad |

Both commands work from players, the console and command blocks, with vanilla selectors and
`~` coordinates. Velocities are in blocks per tick (20 ticks = 1 second).

### /launch

`<targets>` is a player selector: a name, `@p`, `@a[distance=..2]`.

| Form | Effect |
|---|---|
| `set <x> <y> <z>` | Replace velocity. `set 0 2 0` is a strong jump |
| `add <x> <y> <z>` | Add to the current velocity |
| `forward <power> [lift]` | Along where each target looks, times `power`, plus `lift` upward |
| `to <x> <y> <z> [ticks]` | Arc that lands in that block. `ticks` fixes the flight time; without it, the flattest arc that fits under `max-velocity` and clears the target by 1 block |
| `toward <entity> [ticks]` | As `to`, aimed at an entity's feet, e.g. an armor stand used as a marker |

- `to` rounds y down to the block floor: `to 20 100 0` lands on top of the block at y=99, and a
  command block's `~ ~1 ~` means "the block above me". Whole-number x and z are block centres.
- `to` and `toward` skip gliding and flying players.
- If no arc reaches the target, the sender is told: *Too far for one launch at max-velocity...*
- Every launch is capped at `max-velocity` per axis.

### /jumppad

All forms except `list` and `... all` act on the block you look at, within 5 blocks, so only
players can use them.

| Form | Effect |
|---|---|
| `set set\|add <x> <y> <z>` | Pad that sets or adds a velocity |
| `set forward <power> [lift]` | Pad that launches along the player's look direction |
| `set to <x> <y> <z> [ticks]` | Pad that lands players on a block; `~` is relative to you, stored as absolute coordinates |
| `remove` | Forget the pad; the block stays |
| `info` | What the pad does, and whether it is disabled |
| `list` | Every pad, and whether pads are switched off |
| `enable`, `disable` | Switch this pad on or off |
| `enable all`, `disable all` | Switch every pad on or off; each pad keeps its own state |

A pad is a pressure plate (players stand *in* it) or a full block such as slime (players stand
*on* it). A pad is removed when its block, or the block holding up a pad plate, is broken or
blown up. Pads are saved in `plugins/CuriousJumpPads/pads.yml`.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `jumppads.*` | op | All of the below |
| `jumppads.launch` | op | `/launch` |
| `jumppads.admin` | op | `/jumppad` |
| `jumppads.use` | everyone | Being launched by pads |

Command blocks run with full permissions. Pads check `jumppads.use` on each launch, so LuckPerms
contexts can turn pads off by world or region:

```
lp group default permission set jumppads.use false world=lobby
```

## Config

`plugins/CuriousJumpPads/config.yml`, read at startup.

| Key | Default | Meaning |
|---|---|---|
| `max-velocity` | `3.9` | Largest velocity per axis, blocks per tick. Caps every launch |
| `cancel-fall-damage` | `true` | Cancel a launched player's first fall damage... |
| `fall-guard-seconds` | `10` | ...if it comes within this many seconds of the launch |
| `pad-cooldown-ticks` | `10` | Ticks before a pad can launch the same player again |

See [Limits](limits.md) before raising `max-velocity`.
