# Command block design

No plugin pads needed; useful for map builders and adventure maps. `~` in a command block is
relative to the command block's centre. All three layouts are covered by `tests/launch.js`.
Command blocks are on by default in 26.2 (game rule `command_blocks_work`; the old
`enable-command-block` server property is gone).

**A. Plate on the command block** (simplest; command block visible)

```
y+1   [stone pressure plate]
y     [impulse command block]  launch @p[distance=..2] to ~20 ~1 ~
```

**B. Hidden command block** (floor looks normal). The plate strongly powers the block it sits on,
which powers the command block below it.

```
y+2   [stone pressure plate]
y+1   [any solid floor block]
y     [impulse command block]  launch @p[distance=..3] to ~20 ~2 ~
```

**C. No plate** (any decorative block is the pad). An always-active repeating command block
anywhere nearby checks one block space every tick:

```
repeating, Always Active:
execute as @a[x=0,y=100,z=30,dx=0,dy=0,dz=0] run launch @s to ~22 ~2 ~
```

`x,y,z` is the space the player's feet are in (on top of the pad block); `~` is still relative to
the command block, so absolute targets (`to 20 100 30`) are easier to read here.

Tips:

- Use `to` with an absolute target when the landing spot matters, so moving the command block does
  not move the landing.
- Use `[ticks]` for a higher, slower arc (`to 20 100 30 40`), e.g. to clear a wall.
- `@p[distance=..2]` stops a plate launching a player standing next to it.
- Turn off `/gamerule command_block_output` to keep launches out of op chat.
