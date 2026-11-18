package io.tjs.curiousjumppads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.util.Vector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoveGuardTest {
  @ParameterizedTest
  @CsvSource({
    "12, 0, 0, 12, 0.5, true", // one tick at launch speed
    "25, 0, 0, 12, 0.5, true", // two batched ticks
    "26, 0, 0, 12, 0.5, false", // faster than the launch
    "0, -8.8, 0, 12, 0.5, true", // falling near terminal speed
    "0, 10, 0, 12, 0.5, false", // rising far above the launch
    "4, 0, 4, 0, 3.9, false", // sideways on a straight-up launch
  })
  void fitsOnlyTheLaunchSpeed(
      double x, double y, double z, double horizontal, double vertical, boolean fits) {
    assertEquals(fits, MoveGuard.fits(new Vector(x, y, z), horizontal, vertical));
  }
}
