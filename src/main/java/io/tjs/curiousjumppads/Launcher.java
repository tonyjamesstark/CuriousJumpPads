package io.tjs.curiousjumppads;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Turns a {@link LaunchSpec} into a velocity, caps it and applies it to a player. */
public final class Launcher {
  private final FallGuard fallGuard;
  private final double maxVelocity;

  /**
   * Creates a launcher.
   *
   * @param fallGuard armed for each launched player
   * @param maxVelocity the largest velocity per axis, in blocks per tick
   */
  Launcher(FallGuard fallGuard, double maxVelocity) {
    this.fallGuard = fallGuard;
    this.maxVelocity = maxVelocity;
  }

  /**
   * The velocity cap.
   *
   * @return the largest velocity per axis, in blocks per tick
   */
  public double maxVelocity() {
    return maxVelocity;
  }

  /**
   * Launches a player and arms their fall guard.
   *
   * @param player the player
   * @param spec how to launch
   * @return false, launching nothing, if no {@link LaunchSpec.To} arc is found or the player is
   *     gliding or flying
   */
  public boolean launch(Player player, LaunchSpec spec) {
    Optional<Vector> velocity = velocityFor(player, spec);
    velocity.ifPresent(
        v -> {
          player.setVelocity(new Vector(clamp(v.getX()), clamp(v.getY()), clamp(v.getZ())));
          fallGuard.guard(player);
        });
    return velocity.isPresent();
  }

  @SuppressWarnings("deprecation") // isOnGround is client-reported, which is what the arc needs
  private Optional<Vector> velocityFor(Player player, LaunchSpec spec) {
    return switch (spec) {
      case LaunchSpec.Velocity v ->
          Optional.of(v.add() ? player.getVelocity().add(v.velocity()) : v.velocity().clone());
      case LaunchSpec.Forward f ->
          Optional.of(
              player
                  .getLocation()
                  .getDirection()
                  .multiply(f.power())
                  .add(new Vector(0, f.lift(), 0)));
      case LaunchSpec.To t -> {
        if (player.isGliding() || player.isFlying()) {
          yield Optional.empty();
        }
        Vector delta = t.target().clone().subtract(player.getLocation().toVector());
        yield t.ticks() > 0
            ? Ballistics.solve(delta, t.ticks(), player.isOnGround())
            : Ballistics.solve(delta, player.isOnGround(), maxVelocity);
      }
    };
  }

  private double clamp(double v) {
    return Math.max(-maxVelocity, Math.min(maxVelocity, v));
  }
}
