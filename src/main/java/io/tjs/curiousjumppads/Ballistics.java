package io.tjs.curiousjumppads;

import java.util.Optional;
import org.bukkit.util.Vector;

/**
 * Player flight under vanilla client physics. Each tick the player moves by its velocity, then
 * {@code vy = (vy - 0.08) * 0.98} and the horizontal velocity is multiplied by the friction of
 * where the tick started: 0.91 in the air, 0.6 * 0.91 on an ordinary block. A boosted flight has
 * its horizontal velocity put back to the launch's every {@code boostInterval} ticks, except the
 * last boost before it lands: a boost that late reaches the client too close to landing to be
 * corrected if it arrives a tick off.
 */
public final class Ballistics {
  /** Blocks per tick squared, subtracted from vertical velocity each tick. */
  static final double GRAVITY = 0.08;

  /** Vertical velocity multiplier per tick, after gravity. */
  static final double VERTICAL_DRAG = 0.98;

  /** Horizontal velocity multiplier per tick in the air. */
  static final double AIR_FRICTION = 0.91;

  /** Horizontal multiplier for a tick that starts on an ordinary block (slipperiness 0.6). */
  static final double GROUND_FRICTION = 0.6 * AIR_FRICTION;

  /** The longest flight {@link #solve} considers: 20 seconds. */
  public static final int MAX_TICKS = 400;

  /**
   * The shortest flight {@link #solve(Vector, boolean, int, double)} picks: shorter arcs carry so
   * much horizontal speed that the player slides well past the target.
   */
  static final int MIN_TICKS = 10;

  /**
   * The least an arc ends below its target. A client lands only when a move is cut short by the
   * floor, so an arc whose last tick ends exactly on it flies one more tick past the target. Arcs
   * end half their last vertical step below instead, so an error in height smaller than that still
   * lands on the planned tick; this floor only matters for an arc that is barely falling.
   */
  static final double SINK = 0.001;

  /** How far a searched arc peaks above the target, so it clears the edge of a ledge. */
  static final double CLEARANCE = 1.0;

  private Ballistics() {}

  /**
   * Where a flight is after some ticks.
   *
   * @param position displacement from the start
   * @param velocity velocity for the next tick
   * @param apex highest displacement reached, in y
   */
  record State(Vector position, Vector velocity, double apex) {}

  /**
   * Runs the tick physics forward.
   *
   * @param v0 launch velocity
   * @param ticks ticks to run
   * @param onGround whether the first tick starts on the ground
   * @param boostInterval ticks between horizontal boosts, or 0 for none
   * @return the state after {@code ticks} ticks
   */
  static State simulate(Vector v0, int ticks, boolean onGround, int boostInterval) {
    return simulate(v0, ticks, onGround, boostInterval, 0);
  }

  /**
   * As {@link #simulate(Vector, int, boolean, int)}, for a flight {@code phase} ticks into its
   * boost interval.
   *
   * @param v0 velocity now
   * @param ticks ticks to run
   * @param onGround whether the first tick starts on the ground
   * @param boostInterval ticks between horizontal boosts, or 0 for none
   * @param phase ticks since the last boost was due
   * @return the state after {@code ticks} ticks
   */
  static State simulate(Vector v0, int ticks, boolean onGround, int boostInterval, int phase) {
    Vector pos = new Vector();
    Vector vel = v0.clone();
    double apex = 0;
    for (int t = 0; t < ticks; t++) {
      pos.add(vel);
      apex = Math.max(apex, pos.getY());
      double friction = t == 0 && onGround ? GROUND_FRICTION : AIR_FRICTION;
      vel.setX(vel.getX() * friction)
          .setY((vel.getY() - GRAVITY) * VERTICAL_DRAG)
          .setZ(vel.getZ() * friction);
      if (boostInterval > 0
          && (t + 1 + phase) % boostInterval == 0
          && ticks - (t + 1) > boostInterval) {
        vel.setX(v0.getX()).setZ(v0.getZ());
      }
    }
    return new State(pos, vel, apex);
  }

  /**
   * The first tick on which a flight is falling and at or below {@code dy} above its start: when a
   * {@code to} arc reaches its target, or another launch comes back down to where it started.
   *
   * @param v0 launch velocity
   * @param onGround whether the first tick starts on the ground
   * @param boostInterval ticks between horizontal boosts, or 0 for none
   * @param dy landing height relative to the start, in blocks
   * @return that tick, or {@link #MAX_TICKS} if the flight is still above {@code dy} by then
   */
  static int landingTick(Vector v0, boolean onGround, int boostInterval, double dy) {
    for (int t = 1; t < MAX_TICKS; t++) {
      State s = simulate(v0, t, onGround, boostInterval);
      if (s.velocity().getY() < 0 && s.position().getY() <= dy + 1e-6) {
        return t;
      }
    }
    return MAX_TICKS;
  }

  /**
   * The launch velocity that puts the player at {@code delta} from its start after exactly {@code
   * ticks} ticks, sunk below it as {@link #SINK} describes, or empty if it exceeds {@code
   * maxVelocity} on any axis or the player would not be rising at launch and falling on arrival.
   *
   * @param delta target minus start position, in blocks
   * @param ticks flight time
   * @param onGround whether the player starts on the ground (true costs reach on the first tick)
   * @param boostInterval ticks between horizontal boosts, or 0 for none
   * @param maxVelocity the cap per axis, in blocks per tick
   * @return the launch velocity, or empty
   */
  public static Optional<Vector> solve(
      Vector delta, int ticks, boolean onGround, int boostInterval, double maxVelocity) {
    return arc(delta, ticks, onGround, boostInterval).filter(v -> withinCap(v, maxVelocity));
  }

  private static Optional<Vector> arc(
      Vector delta, int ticks, boolean onGround, int boostInterval) {
    Vector v0 = sunk(delta, ticks, onGround, boostInterval);
    if (v0.getY() <= 0 || simulate(v0, ticks, onGround, boostInterval).velocity().getY() >= 0) {
      return Optional.empty();
    }
    return Optional.of(v0);
  }

  private static Vector sunk(Vector delta, int ticks, boolean onGround, int boostInterval) {
    // Sinking barely changes the last step, so one pass sizes it.
    Vector v = exact(delta, ticks, onGround, boostInterval);
    double lastStep = simulate(v, ticks - 1, onGround, boostInterval).velocity().getY();
    Vector below = delta.clone().setY(delta.getY() - Math.max(SINK, -lastStep / 2));
    return exact(below, ticks, onGround, boostInterval);
  }

  private static Vector exact(Vector delta, int ticks, boolean onGround, int boostInterval) {
    // Displacement is linear in the launch velocity: d = v0 * unit + gravityOnly.
    State gravityOnly = simulate(new Vector(), ticks, onGround, boostInterval);
    State unit = simulate(new Vector(1, 1, 1), ticks, onGround, boostInterval);
    double horizontal = unit.position().getX();
    double vertical = unit.position().getY() - gravityOnly.position().getY();
    return new Vector(
        delta.getX() / horizontal,
        (delta.getY() - gravityOnly.position().getY()) / vertical,
        delta.getZ() / horizontal);
  }

  /**
   * The flattest arc to {@code delta} whose velocity is within {@code maxVelocity} per axis and
   * that peaks at least {@link #CLEARANCE} above the target.
   *
   * @param delta target minus start position, in blocks
   * @param onGround whether the player starts on the ground
   * @param boostInterval ticks between horizontal boosts, or 0 for none
   * @param maxVelocity the cap per axis, in blocks per tick
   * @return the launch velocity, or empty if the target is out of reach
   */
  public static Optional<Vector> solve(
      Vector delta, boolean onGround, int boostInterval, double maxVelocity) {
    for (int ticks = MIN_TICKS; ticks <= MAX_TICKS; ticks++) {
      Optional<Vector> v0 = solve(delta, ticks, onGround, boostInterval, maxVelocity);
      if (v0.isPresent()
          && simulate(v0.get(), ticks, onGround, boostInterval).apex()
              >= delta.getY() + CLEARANCE) {
        return v0;
      }
    }
    return Optional.empty();
  }

  /**
   * Checks a velocity against the cap.
   *
   * @param v a velocity
   * @param maxVelocity the cap per axis
   * @return whether every axis of {@code v} is within the cap
   */
  static boolean withinCap(Vector v, double maxVelocity) {
    return Math.abs(v.getX()) <= maxVelocity
        && Math.abs(v.getY()) <= maxVelocity
        && Math.abs(v.getZ()) <= maxVelocity;
  }
}
