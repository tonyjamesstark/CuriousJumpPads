package io.tjs.curiousjumppads;

import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Turns a {@link LaunchSpec} into a velocity, caps it and applies it to a player. */
public final class Launcher {
  private final FallGuard fallGuard;
  private final MoveGuard moveGuard;
  private final LateralBoost lateralBoost;
  private final Supplier<Settings> settings;

  /**
   * Creates a launcher.
   *
   * @param fallGuard armed for each launched player
   * @param moveGuard exempts each launched player from the "moved too quickly" check
   * @param lateralBoost boosts flights launched with {@code boost}
   * @param settings the current config, for {@code max-velocity} and {@code lateral-boost}
   */
  Launcher(
      FallGuard fallGuard,
      MoveGuard moveGuard,
      LateralBoost lateralBoost,
      Supplier<Settings> settings) {
    this.fallGuard = fallGuard;
    this.moveGuard = moveGuard;
    this.lateralBoost = lateralBoost;
    this.settings = settings;
  }

  /** What a launch did. */
  public sealed interface Outcome {
    /** The player was launched. */
    record Launched() implements Outcome {}

    /** A {@link LaunchSpec.To} skipped a gliding or flying player, whose flight it cannot aim. */
    record Skipped() implements Outcome {}

    /**
     * No arc reaches the {@link LaunchSpec.To} target under the cap.
     *
     * @param reason a sentence for the sender
     */
    record Unreachable(String reason) implements Outcome {}
  }

  /**
   * Launches a player, arms their fall and move guards, and starts the lateral boost if the spec
   * asks for it and {@code lateral-boost} is enabled.
   *
   * @param player the player
   * @param spec how to launch
   * @return whether the player was launched, and if not, why
   */
  public Outcome launch(Player player, LaunchSpec spec) {
    Settings s = settings.get();
    double maxVelocity = s.maxVelocity();
    int boostInterval = spec.boost() ? s.boostInterval() : 0;
    @SuppressWarnings("deprecation") // isOnGround is client-reported, which is what the arc needs
    boolean onGround = player.isOnGround();
    Vector target = null;
    Vector velocity;
    switch (spec) {
      case LaunchSpec.Velocity v ->
          velocity = v.add() ? player.getVelocity().add(v.velocity()) : v.velocity().clone();
      case LaunchSpec.Forward f ->
          velocity =
              player
                  .getLocation()
                  .getDirection()
                  .multiply(f.power())
                  .add(new Vector(0, f.lift(), 0));
      case LaunchSpec.To t -> {
        if (player.isGliding() || player.isFlying()) {
          return new Outcome.Skipped();
        }
        Optional<Vector> arc = arc(player, t, onGround, boostInterval, maxVelocity);
        if (arc.isEmpty()) {
          return new Outcome.Unreachable(
              t.ticks() > 0
                  ? "No arc reaches that point in exactly "
                      + t.ticks()
                      + " ticks at max-velocity "
                      + maxVelocity
                      + "."
                  : "Too far for one launch at max-velocity "
                      + maxVelocity
                      + "; chain pads or raise max-velocity.");
        }
        velocity = arc.get();
        target = t.target();
      }
    }
    Vector applied =
        new Vector(
            clamp(velocity.getX(), maxVelocity),
            clamp(velocity.getY(), maxVelocity),
            clamp(velocity.getZ(), maxVelocity));
    player.setVelocity(applied);
    fallGuard.guard(player);
    moveGuard.guard(player, applied);
    if (boostInterval > 0) {
      lateralBoost.start(player, applied, onGround, boostInterval, target, maxVelocity);
    } else {
      lateralBoost.stop(player);
    }
    return new Outcome.Launched();
  }

  private static Optional<Vector> arc(
      Player player, LaunchSpec.To t, boolean onGround, int boostInterval, double maxVelocity) {
    Vector delta = t.target().clone().subtract(player.getLocation().toVector());
    return t.ticks() > 0
        ? Ballistics.solve(delta, t.ticks(), onGround, boostInterval, maxVelocity)
        : Ballistics.solve(delta, onGround, boostInterval, maxVelocity);
  }

  private static double clamp(double v, double maxVelocity) {
    return Math.max(-maxVelocity, Math.min(maxVelocity, v));
  }
}
