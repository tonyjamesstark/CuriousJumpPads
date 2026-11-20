package io.tjs.curiousjumppads;

import io.papermc.paper.event.player.PlayerFailMoveEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/**
 * Lets a launched player pass the server's "moved too quickly" check until they land, as long as
 * each step fits the speed they were launched at.
 */
final class MoveGuard implements Listener {
  /** Batched move packets can each carry a full tick of motion. */
  static final double SLACK = 2;

  /** Falling speed approaches this many blocks per tick. */
  static final double TERMINAL_FALL = 3.92;

  private record Flight(UUID world, double horizontal, double vertical, long expiresAt) {}

  /** The longest a launch stays exempt if the player never lands: the longest solved flight. */
  static final long MAX_FLIGHT_MILLIS = Ballistics.MAX_TICKS * 50L;

  private final Supplier<Settings> settings;
  private final Map<UUID, Flight> flights = new HashMap<>();
  private final Set<UUID> airborne = new HashSet<>();

  /**
   * Creates the guard.
   *
   * @param settings the current config; when {@code allow-fast-launches} is false, {@link #guard}
   *     does nothing
   */
  MoveGuard(Supplier<Settings> settings) {
    this.settings = settings;
  }

  /**
   * Exempts a player who was just launched, replacing any earlier launch.
   *
   * @param player the launched player
   * @param velocity the velocity they were given
   */
  void guard(Player player, Vector velocity) {
    if (!settings.get().allowFastLaunches()) {
      return;
    }
    flights.put(
        player.getUniqueId(),
        new Flight(
            player.getWorld().getUID(),
            Math.hypot(velocity.getX(), velocity.getZ()),
            Math.abs(velocity.getY()),
            System.currentTimeMillis() + MAX_FLIGHT_MILLIS));
    airborne.remove(player.getUniqueId());
  }

  /**
   * Whether one move step could come from a launch at these speeds.
   *
   * @param step the movement the server is checking
   * @param horizontal the launch's horizontal speed, in blocks per tick
   * @param vertical the launch's vertical speed, in blocks per tick
   * @return true if the step is within {@link #SLACK} ticks of that speed, plus a block
   */
  static boolean fits(Vector step, double horizontal, double vertical) {
    return Math.hypot(step.getX(), step.getZ()) <= horizontal * SLACK + 1
        && Math.abs(step.getY()) <= Math.max(vertical, TERMINAL_FALL) * SLACK + 1;
  }

  /**
   * Allows a guarded player's "moved too quickly" move if it fits their launch.
   *
   * @param event a move the server would reject
   */
  @EventHandler
  void onFailMove(PlayerFailMoveEvent event) {
    if (event.getFailReason() != PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
      return;
    }
    Player player = event.getPlayer();
    Flight flight = flights.get(player.getUniqueId());
    if (flight == null) {
      return;
    }
    if (flight.expiresAt() < System.currentTimeMillis()
        || !flight.world().equals(player.getWorld().getUID())
        || player.isFlying()
        || player.isGliding()
        || player.isInsideVehicle()
        || player.getGameMode() == GameMode.SPECTATOR) {
      forget(player);
      return;
    }
    Vector step = event.getTo().toVector().subtract(event.getFrom().toVector());
    if (fits(step, flight.horizontal(), flight.vertical())) {
      event.setAllowed(true);
      event.setLogWarning(false);
    }
  }

  /**
   * Ends a guarded player's exemption when they land.
   *
   * @param event any player move
   */
  @SuppressWarnings("deprecation") // isOnGround is client-reported; it only ever ends the exemption
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    UUID id = player.getUniqueId();
    if (!flights.containsKey(id)) {
      return;
    }
    if (!player.isOnGround()) {
      airborne.add(id);
    } else if (airborne.contains(id)) {
      forget(player);
    }
  }

  /**
   * Ends the exemption on any teleport, since the launch no longer describes the motion.
   *
   * @param event the teleport
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onTeleport(PlayerTeleportEvent event) {
    forget(event.getPlayer());
  }

  /**
   * Ends the exemption on a world change.
   *
   * @param event the change
   */
  @EventHandler
  void onWorldChange(PlayerChangedWorldEvent event) {
    forget(event.getPlayer());
  }

  /**
   * Ends the exemption on death.
   *
   * @param event the death
   */
  @EventHandler
  void onDeath(PlayerDeathEvent event) {
    forget(event.getPlayer());
  }

  /**
   * Forgets a player who leaves.
   *
   * @param event the quit
   */
  @EventHandler
  void onQuit(PlayerQuitEvent event) {
    forget(event.getPlayer());
  }

  private void forget(Player player) {
    flights.remove(player.getUniqueId());
    airborne.remove(player.getUniqueId());
  }
}
