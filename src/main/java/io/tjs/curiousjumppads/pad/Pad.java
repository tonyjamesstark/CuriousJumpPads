package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.LaunchSpec;

/**
 * A jump pad.
 *
 * @param spec the launch it gives
 * @param enabled whether it launches; {@code /jumppad disable} clears this
 */
public record Pad(LaunchSpec spec, boolean enabled) {
  /**
   * A copy with a new state.
   *
   * @param enabled the new state
   * @return this pad, switched on or off
   */
  Pad withEnabled(boolean enabled) {
    return new Pad(spec, enabled);
  }
}
