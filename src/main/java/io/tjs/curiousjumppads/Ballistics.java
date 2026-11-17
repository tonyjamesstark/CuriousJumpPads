package io.tjs.curiousjumppads;

import java.util.Optional;
import org.bukkit.util.Vector;

/**
 * Player flight under vanilla client physics. Each tick the player moves by its velocity, then
 * {@code vy = (vy - 0.08) * 0.98} and the horizontal velocity is multiplied by the friction of
 * where the tick started: 0.91 in the air, 0.6 * 0.91 on an ordinary block.
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
   * The shortest flight {@link #solve(Vector, boolean, double)} picks: shorter arcs carry so much
   * horizontal speed that the player slides well past the target.
   */
  static final int MIN_TICKS = 10;

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
   * @return the state after {@code ticks} ticks
   */
  static State simulate(Vector v0, int ticks, boolean onGround) {
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
    }
    return new State(pos, vel, apex);
  }

  /**
   * The launch velocity that puts the player at {@code delta} from its start after exactly {@code
   * ticks} ticks, or empty if the player would not be rising at launch and falling on arrival.
   *
   * @param delta target minus start position, in blocks
   * @param ticks flight time
   * @param onGround whether the player starts on the ground (true costs reach on the first tick)
   * @return the launch velocity, or empty
   */
  public static Optional<Vector> solve(Vector delta, int ticks, boolean onGround) {
    // Displacement is linear in the launch velocity: d = v0 * unit + gravityOnly.
    State gravityOnly = simulate(new Vector(), ticks, onGround);
    State unit = simulate(new Vector(1, 1, 1), ticks, onGround);
    double horizontal = unit.position().getX();
    double vertical = unit.position().getY() - gravityOnly.position().getY();
    Vector v0 =
        new Vector(
            delta.getX() / horizontal,
            (delta.getY() - gravityOnly.position().getY()) / vertical,
            delta.getZ() / horizontal);
    if (v0.getY() <= 0 || simulate(v0, ticks, onGround).velocity().getY() >= 0) {
      return Optional.empty();
    }
    return Optional.of(v0);
  }

  /**
   * The flattest arc to {@code delta} whose velocity is within {@code maxVelocity} per axis and
   * that peaks at least {@link #CLEARANCE} above the target.
   *
   * @param delta target minus start position, in blocks
   * @param onGround whether the player starts on the ground
   * @param maxVelocity the cap per axis, in blocks per tick
   * @return the launch velocity, or empty if the target is out of reach
   */
  public static Optional<Vector> solve(Vector delta, boolean onGround, double maxVelocity) {
    for (int ticks = MIN_TICKS; ticks <= MAX_TICKS; ticks++) {
      Optional<Vector> v0 = solve(delta, ticks, onGround);
      if (v0.isPresent()
          && withinCap(v0.get(), maxVelocity)
          && simulate(v0.get(), ticks, onGround).apex() >= delta.getY() + CLEARANCE) {
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
