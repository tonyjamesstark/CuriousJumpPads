# Quick start

Set up a jump pad that lands players on a chosen block, in about five minutes. You need op on
a Paper or Purpur 26.2 server. Every command is listed in [commands.md](commands.md).

## 1. Install

1. Stop the server.
2. Put `CuriousJumpPads-<version>.jar` in `plugins/`.
3. Start the server. The log shows `Enabling CuriousJumpPads`.

## 2. Try a launch

Stand on open ground and run:

```
/launch @s set 0 2 0
```

You go straight up about 20 blocks and land without fall damage: a launch cancels your next fall
damage for 10 seconds.

## 3. Pick a landing spot

Walk to where players should land, at most about 25 blocks from the take-off spot. Press F3,
look down at the block you stand on and note its *Targeted Block* coordinates, say
`120 64 -40`. The landing block is the one above it: `120 65 -40`.

Walk back to the take-off spot and check you land there:

```
/launch @s to 120 65 -40
```

If you are told *Too far for one launch*, pick a closer spot, or plan two pads with a
landing in between.

## 4. Make the pad

1. Place a pressure plate where players take off.
2. Look at the plate from up to 5 blocks away and run:

   ```
   /jumppad set to 120 65 -40
   ```

   The reply is `Jump pad at world <x> <y> <z>: to 120.50 65.00 -39.50`.
3. Step on the plate. You land on the block you picked.

A slime block or any full block works too: look at it instead of a plate. Players then launch
when they stand on it.

## 5. Adjust

| To | Run (looking at the pad) |
|---|---|
| Make the arc higher and slower | `/jumppad set to 120 65 -40 40` (40 ticks = 2 s in the air) |
| See what the pad does | `/jumppad info` |
| Switch it off for a while | `/jumppad disable`, later `/jumppad enable` |
| Switch every pad off | `/jumppad disable all` |
| Remove it | `/jumppad remove`, or break the plate |

## Alternative: command blocks

On a map where you would rather not use plugin pads, put an impulse command block under the
pressure plate with:

```
launch @p[distance=..2] to ~20 ~1 ~
```

That lands players 20 blocks east, one block up from the command block, which is the floor
level the plate sits on. The [command block page](command-blocks.md) shows a hidden layout
and one without a plate.

## Next

- Limit pads to some worlds: [Permissions](commands.md#permissions).
- Reach, accuracy and the velocity cap: [Limits](limits.md).
