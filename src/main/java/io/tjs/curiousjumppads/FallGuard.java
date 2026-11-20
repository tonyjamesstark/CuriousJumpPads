package io.tjs.curiousjumppads;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Cancels the first fall damage a launched player takes before the guard expires. */
final class FallGuard implements Listener {
  private final Supplier<Settings> settings;
  private final Map<UUID, Long> expiresAt = new HashMap<>();

  /**
   * Creates the guard.
   *
   * @param settings the current config; when {@code cancel-fall-damage.enabled} is false, {@link
   *     #guard} does nothing
   */
  FallGuard(Supplier<Settings> settings) {
    this.settings = settings;
  }

  /**
   * Arms the guard for a player who was just launched.
   *
   * @param player the launched player
   */
  void guard(Player player) {
    Settings s = settings.get();
    if (s.cancelFallDamage()) {
      expiresAt.put(player.getUniqueId(), System.currentTimeMillis() + s.fallGuardMillis());
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
