package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.Launcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
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
  private final IntSupplier cooldownTicks;
  private final Map<UUID, Integer> lastLaunchTick = new HashMap<>();

  /**
   * Creates the listener.
   *
   * @param store the pads
   * @param launcher applies a pad's launch
   * @param cooldownTicks the current {@code pad-cooldown-ticks}: ticks before a pad without its own
   *     cooldown can launch the same player again
   */
  public PadListener(PadStore store, Launcher launcher, IntSupplier cooldownTicks) {
    this.store = store;
    this.launcher = launcher;
    this.cooldownTicks = cooldownTicks;
  }

  /**
   * Launches a player whose feet move into a pad plate's block or onto a pad block, if pads are on,
   * this pad is on, the player has {@code jumppads.use} and is past the cooldown.
   *
   * @param event any player move
   */
  @EventHandler(ignoreCancelled = true)
  void onMove(PlayerMoveEvent event) {
    if (!event.hasChangedBlock() || !store.enabled()) {
      return;
    }
    Player player = event.getPlayer();
    Pad pad = store.padUnder(BlockKey.of(event.getTo().getBlock()));
    if (pad == null || !pad.enabled() || !player.hasPermission("jumppads.use")) {
      return;
    }
    int now = Bukkit.getCurrentTick();
    Integer last = lastLaunchTick.get(player.getUniqueId());
    if (last != null && now - last < pad.cooldownTicks().orElseGet(cooldownTicks)) {
      return;
    }
    if (launcher.launch(player, pad.spec()) instanceof Launcher.Outcome.Launched) {
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
    List<BlockKey> keys = new ArrayList<>();
    for (Block block : blocks) {
      keys.add(BlockKey.of(block));
      Block above = block.getRelative(BlockFace.UP);
      if (Tag.PRESSURE_PLATES.isTagged(above.getType())) {
        keys.add(BlockKey.of(above));
      }
    }
    return store.removeAll(keys);
  }
}
