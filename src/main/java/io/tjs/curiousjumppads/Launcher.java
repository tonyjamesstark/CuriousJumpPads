package io.tjs.curiousjumppads;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Turns a {@link LaunchSpec} into a velocity, caps it and applies it to a player. */
public final class Launcher {
  private final FallGuard fallGuard;
  private final MoveGuard moveGuard;
  private final double maxVelocity;

  /**
   * Creates a launcher.
   *
   * @param fallGuard armed for each launched player
   * @param moveGuard exempts each launched player from the "moved too quickly" check
   * @param maxVelocity the largest velocity per axis, in blocks per tick
   */
  Launcher(FallGuard fallGuard, MoveGuard moveGuard, double maxVelocity) {
    this.fallGuard = fallGuard;
    this.moveGuard = moveGuard;
    this.maxVelocity = maxVelocity;
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
   * Launches a player and arms their fall and move guards.
   *
   * @param player the player
   * @param spec how to launch
   * @return whether the player was launched, and if not, why
   */
  public Outcome launch(Player player, LaunchSpec spec) {
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
        Optional<Vector> arc = arc(player, t);
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
      }
    }
    Vector applied =
        new Vector(clamp(velocity.getX()), clamp(velocity.getY()), clamp(velocity.getZ()));
    player.setVelocity(applied);
    fallGuard.guard(player);
    moveGuard.guard(player, applied);
    return new Outcome.Launched();
  }

  @SuppressWarnings("deprecation") // isOnGround is client-reported, which is what the arc needs
  private Optional<Vector> arc(Player player, LaunchSpec.To t) {
    Vector delta = t.target().clone().subtract(player.getLocation().toVector());
    return t.ticks() > 0
        ? Ballistics.solve(delta, t.ticks(), player.isOnGround(), maxVelocity)
        : Ballistics.solve(delta, player.isOnGround(), maxVelocity);
  }

  private double clamp(double v) {
    return Math.max(-maxVelocity, Math.min(maxVelocity, v));
  }
}
