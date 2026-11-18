package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.LaunchSpec;
import java.util.OptionalInt;

/**
 * A jump pad.
 *
 * @param spec the launch it gives
 * @param enabled whether it launches; {@code /jumppad disable} clears this
 * @param cooldownTicks ticks before it launches the same player again, or empty for {@code
 *     pad-cooldown-ticks}; set by {@code /jumppad cooldown}
 */
public record Pad(LaunchSpec spec, boolean enabled, OptionalInt cooldownTicks) {
  /**
   * A copy with a new state.
   *
   * @param enabled the new state
   * @return this pad, switched on or off
   */
  Pad withEnabled(boolean enabled) {
    return new Pad(spec, enabled, cooldownTicks);
  }

  /**
   * A copy with a new cooldown.
   *
   * @param cooldownTicks the new cooldown, or empty for {@code pad-cooldown-ticks}
   * @return this pad with that cooldown
   */
  Pad withCooldown(OptionalInt cooldownTicks) {
    return new Pad(spec, enabled, cooldownTicks);
  }

  /**
   * A one-line summary for chat, such as {@code forward 1.5 lift 0.6, cooldown 40 ticks
   * (disabled)}.
   *
   * @return the summary
   */
  String describe() {
    return spec.describe()
        + (cooldownTicks.isPresent() ? ", cooldown " + cooldownTicks.getAsInt() + " ticks" : "")
        + (enabled ? "" : " (disabled)");
  }
}
