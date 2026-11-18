# Limits

- **Reach.** At 3.9 a single launch reaches about 27 blocks from the ground and 43 from the air:
  the first tick from the ground loses ~45% of horizontal speed to ground friction. Chain pads
  for longer distances.
- **Raising `max-velocity`** (see [config](commands.md#config)). 26.2 sends velocity
  without the old ±3.9 packet limit, so higher values work. A move of more than about 10
  blocks in one tick fails the server's "moved too quickly" check, which would pull the
  player back; `allow-fast-launches` exempts a launched player until they land, but only for
  moves that fit the speed they were launched at, so it cannot be used as a speed hack.
  An anticheat plugin has its own checks and may still flag fast launches.
- **Accuracy.** Movement is client-side, so the arc assumes vanilla physics. Slow falling,
  levitation, jump boost, elytra and laggy clients land off target. A player holding *forward*
  while launched lands up to about a block further along.
- **Slabs and stairs.** `to` lands on the floor of the target block, so aiming at a slab top lands
  half a block short.
- **Fall guard.** A launch that lands without fall damage leaves the guard armed until it expires,
  so an unrelated fall in that window is also cancelled.
