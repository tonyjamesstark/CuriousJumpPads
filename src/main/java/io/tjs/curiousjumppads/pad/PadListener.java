package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.Launcher;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Launches players standing in a pad block (a pressure plate) or on one (a full block), and forgets
 * pads whose block, or the block holding up a plate pad, is broken or blown up.
 */
public final class PadListener implements Listener {
  private final PadStore store;
  private final Launcher launcher;
  private final int cooldownTicks;
  private final Logger log;
  private final Map<UUID, Integer> lastLaunchTick = new HashMap<>();

  /**
   * Creates the listener.
   *
   * @param store the pads
   * @param launcher applies a pad's launch
   * @param cooldownTicks {@code pad-cooldown-ticks}: ticks before the same player can be launched
   *     again
   * @param log where failures to save {@code pads.yml} go
   */
  public PadListener(PadStore store, Launcher launcher, int cooldownTicks, Logger log) {
    this.store = store;
    this.launcher = launcher;
    this.cooldownTicks = cooldownTicks;
    this.log = log;
  }

  /**
   * Launches a player who moves into a pad plate or onto a pad block, if pads are on, this pad is
   * on, the player has {@code jumppads.use} and is past the cooldown.
   *
   * @param event any player move
   */
  @EventHandler(ignoreCancelled = true)
  void onMove(PlayerMoveEvent event) {
    if (!event.hasChangedPosition() || !store.enabled()) {
      return;
    }
    Player player = event.getPlayer();
    Block feet = event.getTo().getBlock();
    Pad pad = store.get(BlockKey.of(feet));
    if (pad == null) {
      pad = store.get(BlockKey.of(feet.getRelative(BlockFace.DOWN)));
    }
    if (pad == null || !pad.enabled() || !player.hasPermission("jumppads.use")) {
      return;
    }
    int now = Bukkit.getCurrentTick();
    Integer last = lastLaunchTick.get(player.getUniqueId());
    if (last != null && now - last < cooldownTicks) {
      return;
    }
    if (launcher.launch(player, pad.spec())) {
      lastLaunchTick.put(player.getUniqueId(), now);
    }
  }

  /**
   * Forgets a pad whose block, or whose plate's supporting block, is broken.
   *
   * @param event any block break
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onBreak(BlockBreakEvent event) {
    if (forget(List.of(event.getBlock())) && event.getPlayer().hasPermission("jumppads.admin")) {
      event.getPlayer().sendMessage("Jump pad removed.");
    }
  }

  /**
   * Forgets pads destroyed by a block explosion, such as a bed in the nether.
   *
   * @param event the explosion
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onBlockExplode(BlockExplodeEvent event) {
    forget(event.blockList());
  }

  /**
   * Forgets pads destroyed by an entity explosion, such as TNT or a creeper.
   *
   * @param event the explosion
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityExplode(EntityExplodeEvent event) {
    forget(event.blockList());
  }

  /**
   * Forgets a player's cooldown when they leave.
   *
   * @param event the quit
   */
  @EventHandler
  void onQuit(PlayerQuitEvent event) {
    lastLaunchTick.remove(event.getPlayer().getUniqueId());
  }

  private boolean forget(List<Block> blocks) {
    boolean removed = false;
    for (Block block : blocks) {
      try {
        removed |= store.remove(BlockKey.of(block));
        Block above = block.getRelative(BlockFace.UP);
        if (Tag.PRESSURE_PLATES.isTagged(above.getType())) {
          removed |= store.remove(BlockKey.of(above));
        }
      } catch (IOException e) {
        log.log(Level.WARNING, "Could not save pads.yml", e);
      }
    }
    return removed;
  }
}
