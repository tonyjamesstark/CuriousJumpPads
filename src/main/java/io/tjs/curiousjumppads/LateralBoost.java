package io.tjs.curiousjumppads;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
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
 * Boosts a launched player's horizontal speed back to the launch's every few moves of their flight,
 * until the move the arc predicts they land on, or sooner if they land, teleport or start flying.
 *
 * <p>Flight time is counted in the client's own moves, not server ticks: a lagging server or
 * network jitter would otherwise put a boost a tick early or late and shift the rest of the arc.
 * Each boost carries the vertical speed the player's own fall will have when it arrives, and sets
 * only the horizontal: the launch's, or for a target, what reaches it from the player's reported
 * position. The last boost before landing is left out, as {@link Ballistics} plans.
 */
final class LateralBoost implements Listener {
  /** Server ticks to wait for the first move that shows the launch before giving up. */
  static final int DETECT_TICKS = 40;

  /**
   * How close a move's step must be to a sent velocity to count as that velocity taking effect:
   * more than a player's own air control adds in a tick, less than one tick of air drag at speed.
   */
  static final double MATCH = 0.05;

  /**
   * As {@link #MATCH} for the launch itself, whose first move also carries ground walking input.
   */
  static final double LAUNCH_MATCH = 0.2;

  /** Moves after which a sent velocity that never showed up is no longer waited for. */
  static final int GIVE_UP_MOVES = 10;

  private record Sent(Vector velocity, int forMove) {}

  private static final class Flight {
    final UUID world;
    final Vector v0;
    final boolean onGround;
    final int interval;
    final int landingMove;
    final Vector target;
    final double maxVelocity;
    final int startTick;
    final ArrayDeque<Sent> sent = new ArrayDeque<>();
    int moves;
    int lag;
    // Moves the flight runs behind its plan, from velocities that took effect late; negative if
    // early. The boost schedule and the landing move shift with it.
    int shift;
    boolean airborne;

    Flight(
        Player player,
        Vector v0,
        boolean onGround,
        int interval,
        Vector target,
        double maxVelocity) {
      this.world = player.getWorld().getUID();
      this.v0 = v0.clone();
      this.onGround = onGround;
      this.interval = interval;
      this.target = target == null ? null : target.clone();
      this.maxVelocity = maxVelocity;
      this.startTick = Bukkit.getCurrentTick();
      double landingDy = target == null ? 0 : target.getY() - player.getLocation().getY();
      this.landingMove = Ballistics.landingTick(v0, onGround, interval, landingDy);
    }
  }

  private final Map<UUID, Flight> flights = new HashMap<>();

  /** Created by the plugin, which runs {@link #tick} every tick. */
  LateralBoost() {}

  /**
   * Starts boosting a player who was just launched, replacing any earlier flight.
   *
   * @param player the launched player
   * @param v0 the velocity they were given
   * @param onGround whether they launched from the ground
   * @param interval moves between boosts
   * @param target where a {@code to} arc lands, or null for a launch without a target
   * @param maxVelocity the cap per axis for a re-aimed boost
   */
  void start(
      Player player, Vector v0, boolean onGround, int interval, Vector target, double maxVelocity) {
    flights.put(
        player.getUniqueId(), new Flight(player, v0, onGround, interval, target, maxVelocity));
  }

  /**
   * Stops boosting a player, such as one relaunched without {@code boost}.
   *
   * @param player the player
   */
  void stop(Player player) {
    flights.remove(player.getUniqueId());
  }

  /** Drops flights that can no longer follow their arc, or whose launch never showed up. */
  void tick() {
    int now = Bukkit.getCurrentTick();
    flights
        .entrySet()
        .removeIf(
            e -> {
              Flight f = e.getValue();
              Player player = Bukkit.getPlayer(e.getKey());
              int age = now - f.startTick;
              return player == null
                  || (f.moves == 0 && age > DETECT_TICKS)
                  || age > DETECT_TICKS + Ballistics.MAX_TICKS
                  || !f.world.equals(player.getWorld().getUID())
                  || player.isFlying()
                  || player.isGliding()
                  || player.isInsideVehicle()
                  || player.getGameMode() == GameMode.SPECTATOR;
            });
  }

  /**
   * Counts a boosted player's moves, sends the boosts due, and ends the flight when they land or
   * reach the predicted landing.
   *
   * @param event any player move
   */
  @SuppressWarnings("deprecation") // isOnGround is client-reported; it only ever ends the flight
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Flight f = flights.get(player.getUniqueId());
    if (f == null) {
      return;
    }
    Vector step = event.getTo().toVector().subtract(event.getFrom().toVector());
    if (f.moves == 0 && step.distance(f.v0) >= LAUNCH_MATCH) {
      return; // a move the client made before the launch reached it
    }
    f.moves++;
    boolean late = measureLag(f, step);
    boolean aimed = f.target != null;
    if (!player.isOnGround()) {
      f.airborne = true;
    } else if (f.airborne) {
      stop(player);
      return;
    }
    int landingMove = f.landingMove + f.shift;
    if (f.moves >= landingMove) {
      stop(player);
      return;
    }
    // A velocity sent now is first used by the move after the `lag` the client makes meanwhile.
    int move = f.moves + f.lag + 1;
    int planned = move - f.shift;
    if (move > landingMove || (Math.floorMod(planned - 1, f.interval) != 0 && !(late && aimed))) {
      return;
    }
    if (landingMove - move + 1 <= f.interval) {
      return; // the last boost, which Ballistics leaves out
    }
    Vector velocity = forecast(event.getTo().toVector(), step, f, move, planned);
    player.setVelocity(velocity);
    f.sent.add(new Sent(velocity, move));
  }

  // Finds which sent velocity this move used, if any, and corrects the lag and the shift by how
  // late
  // or early it took effect, returning whether it was off. Velocities sent a move apart can be too
  // alike to tell apart; those only clear the queue.
  private static boolean measureLag(Flight f, Vector step) {
    f.sent.removeIf(s -> f.moves - s.forMove() > GIVE_UP_MOVES);
    List<Sent> hits = f.sent.stream().filter(s -> close(step, s.velocity())).toList();
    if (hits.size() == 1) {
      Sent hit = hits.getFirst();
      while (f.sent.poll() != hit) {}
      int off = f.moves - hit.forMove();
      f.lag = Math.max(0, f.lag + off);
      f.shift += off;
      return off != 0;
    }
    if (hits.size() > 1) {
      f.sent.removeIf(s -> s.forMove() <= f.moves);
    }
    return false;
  }

  // Steps the client's last move forward to the one the boost arrives on, using velocities already
  // sent for the moves between, and keeps the vertical speed it forecasts there so a late boost
  // does not rewind the fall. Only the horizontal is set: the launch's, or for a target, what
  // covers
  // the rest of the way by the move the forecast falls through the target's height.
  private static Vector forecast(Vector pos, Vector step, Flight f, int move, int planned) {
    Vector at = pos.clone();
    Vector v = step.clone();
    for (int i = f.moves + 1; i <= move; i++) {
      int forMove = i;
      v =
          f.sent.stream()
              .filter(s -> s.forMove() == forMove)
              .map(s -> s.velocity().clone())
              .findFirst()
              .orElse(decay(v));
      if (i < move) {
        at.add(v);
      }
    }
    if (f.target == null) {
      return new Vector(f.v0.getX(), v.getY(), f.v0.getZ());
    }
    double y = at.getY();
    double vy = v.getY();
    int ticks = 0;
    while (ticks < Ballistics.MAX_TICKS && (ticks == 0 || vy >= 0 || y > f.target.getY())) {
      y += vy;
      vy = (vy - Ballistics.GRAVITY) * Ballistics.VERTICAL_DRAG;
      ticks++;
    }
    int phase = Math.floorMod(planned - 1, f.interval);
    double unit =
        Ballistics.simulate(new Vector(1, 0, 0), ticks, false, f.interval, phase).position().getX();
    double cap = f.maxVelocity;
    return new Vector(
        Math.clamp((f.target.getX() - at.getX()) / unit, -cap, cap),
        v.getY(),
        Math.clamp((f.target.getZ() - at.getZ()) / unit, -cap, cap));
  }

  private static Vector decay(Vector v) {
    return v.clone()
        .setX(v.getX() * Ballistics.AIR_FRICTION)
        .setY((v.getY() - Ballistics.GRAVITY) * Ballistics.VERTICAL_DRAG)
        .setZ(v.getZ() * Ballistics.AIR_FRICTION);
  }

  private static boolean close(Vector step, Vector velocity) {
    return step.distance(velocity) < MATCH;
  }

  /**
   * Ends the flight on any teleport.
   *
   * @param event the teleport
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onTeleport(PlayerTeleportEvent event) {
    stop(event.getPlayer());
  }

  /**
   * Ends the flight on a world change.
   *
   * @param event the change
   */
  @EventHandler
  void onWorldChange(PlayerChangedWorldEvent event) {
    stop(event.getPlayer());
  }

  /**
   * Ends the flight on death.
   *
   * @param event the death
   */
  @EventHandler
  void onDeath(PlayerDeathEvent event) {
    stop(event.getPlayer());
  }

  /**
   * Forgets a player who leaves.
   *
   * @param event the quit
   */
  @EventHandler
  void onQuit(PlayerQuitEvent event) {
    stop(event.getPlayer());
  }
}
