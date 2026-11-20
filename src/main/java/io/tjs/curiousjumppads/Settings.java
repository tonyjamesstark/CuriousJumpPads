package io.tjs.curiousjumppads;

import org.bukkit.configuration.Configuration;

/**
 * The values in {@code config.yml}.
 *
 * @param maxVelocity {@code max-velocity}: the largest velocity per axis, in blocks per tick
 * @param allowFastLaunches {@code allow-fast-launches}
 * @param cancelFallDamage {@code cancel-fall-damage.enabled}
 * @param fallGuardMillis {@code cancel-fall-damage.timeout-seconds}, in milliseconds
 * @param padCooldownTicks {@code pad-cooldown-ticks}
 * @param boostInterval {@code lateral-boost.interval-ticks}, at least 1, or 0 if {@code
 *     lateral-boost.enabled} is false
 */
record Settings(
    double maxVelocity,
    boolean allowFastLaunches,
    boolean cancelFallDamage,
    long fallGuardMillis,
    int padCooldownTicks,
    int boostInterval) {

  /**
   * Reads the settings, with the bundled {@code config.yml} filling any missing key.
   *
   * @param config the plugin's config
   * @return the settings
   */
  static Settings from(Configuration config) {
    return new Settings(
        config.getDouble("max-velocity"),
        config.getBoolean("allow-fast-launches"),
        config.getBoolean("cancel-fall-damage.enabled"),
        config.getLong("cancel-fall-damage.timeout-seconds") * 1000,
        config.getInt("pad-cooldown-ticks"),
        config.getBoolean("lateral-boost.enabled")
            ? Math.max(1, config.getInt("lateral-boost.interval-ticks"))
            : 0);
  }
}
