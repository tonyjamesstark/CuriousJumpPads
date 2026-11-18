package io.tjs.curiousjumppads;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Cancels the first fall damage a launched player takes before the guard expires. */
final class FallGuard implements Listener {
  private final boolean enabled;
  private final long durationMillis;
  private final Map<UUID, Long> expiresAt = new HashMap<>();

  /**
   * Creates the guard.
   *
   * @param enabled {@code cancel-fall-damage.enabled}; when false, {@link #guard} does nothing
   * @param durationMillis {@code cancel-fall-damage.timeout-seconds}: how long after a launch the
   *     guard stays armed
   */
  FallGuard(boolean enabled, long durationMillis) {
    this.enabled = enabled;
    this.durationMillis = durationMillis;
  }

  /**
   * Arms the guard for a player who was just launched.
   *
   * @param player the launched player
   */
  void guard(Player player) {
    if (enabled) {
      expiresAt.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
    }
  }

  /**
   * Cancels the fall damage of a guarded player and disarms the guard.
   *
   * @param event any entity damage
   */
  @EventHandler(ignoreCancelled = true)
  void onDamage(EntityDamageEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.FALL
        || !(event.getEntity() instanceof Player player)) {
      return;
    }
    Long expiry = expiresAt.remove(player.getUniqueId());
    if (expiry != null && expiry >= System.currentTimeMillis()) {
      event.setCancelled(true);
    }
  }

  /**
   * Forgets a player who leaves.
   *
   * @param event the quit
   */
  @EventHandler
  void onQuit(PlayerQuitEvent event) {
    expiresAt.remove(event.getPlayer().getUniqueId());
  }
}
