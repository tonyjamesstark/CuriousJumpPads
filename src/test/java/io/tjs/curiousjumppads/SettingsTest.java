package io.tjs.curiousjumppads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettingsTest {
  @ParameterizedTest
  @CsvSource({
    "true, 10, 10",
    "true, 2, 2",
    "true, 1, 1",
    "true, 0, 1",
    "true, -5, 1",
    "false, 10, 0", // 0 is what Launcher reads as no boost
  })
  void boostInterval(boolean enabled, int interval, int expected) throws Exception {
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(
        "lateral-boost:\n  enabled: " + enabled + "\n  interval-ticks: " + interval + "\n");
    assertEquals(expected, Settings.from(config).boostInterval());
  }
}
