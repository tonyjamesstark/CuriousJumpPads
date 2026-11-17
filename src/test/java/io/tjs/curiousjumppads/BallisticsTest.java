package io.tjs.curiousjumppads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BallisticsTest {
  private static final double CAP = 3.9;

  @ParameterizedTest
  @CsvSource({
    "0, 0, 5, true", // short hop
    "25, 0, 0, true", // flat
    "20, 15, 10, true", // uphill
    "-25, -20, 5, true", // downhill
    "0, 40, 0, false", // straight up, from the air
    "38, 0, 0, false", // near the reach at 3.9, from the air
  })
  void landsOnTargetWhileFalling(double x, double y, double z, boolean onGround) {
    Vector delta = new Vector(x, y, z);
    Vector v0 = Ballistics.solve(delta, onGround, CAP).orElseThrow();

    assertTrue(Ballistics.withinCap(v0, CAP), v0::toString);
    int ticks = ticksToReach(v0, delta, onGround);
    Ballistics.State end = Ballistics.simulate(v0, ticks, onGround);
    assertEquals(0, end.position().distance(delta), 1e-6);
    assertTrue(end.velocity().getY() < 0, "still rising on arrival");
    assertTrue(end.apex() >= y + Ballistics.CLEARANCE, "arc does not clear the target");
  }

  @Test
  void airborneMatchesClosedForm() {
    Vector v0 = new Vector(2, 1.5, -1);
    int t = 37;
    Vector pos = Ballistics.simulate(v0, t, false).position();

    double h = (1 - Math.pow(0.91, t)) / 0.09;
    double terminal = -0.08 * 0.98 / 0.02;
    double y = t * terminal + (1.5 - terminal) * (1 - Math.pow(0.98, t)) / 0.02;
    assertEquals(2 * h, pos.getX(), 1e-9);
    assertEquals(y, pos.getY(), 1e-9);
    assertEquals(-h, pos.getZ(), 1e-9);
  }

  @Test
  void groundFrictionOnTheLaunchTickShortensReach() {
    Vector far = new Vector(35, 0, 0);
    assertTrue(Ballistics.solve(far, false, CAP).isPresent());
    assertEquals(Optional.empty(), Ballistics.solve(far, true, CAP));
  }

  @Test
  void tooFarForOneLaunch() {
    assertEquals(Optional.empty(), Ballistics.solve(new Vector(200, 0, 0), true, CAP));
  }

  @Test
  void fixedFlightTimeIsHonoured() {
    Vector delta = new Vector(10, 0, 0);
    Vector v0 = Ballistics.solve(delta, 40, true).orElseThrow();
    assertEquals(0, Ballistics.simulate(v0, 40, true).position().distance(delta), 1e-6);
  }

  private static int ticksToReach(Vector v0, Vector delta, boolean onGround) {
    for (int t = 1; t <= Ballistics.MAX_TICKS; t++) {
      if (Ballistics.simulate(v0, t, onGround).position().distance(delta) < 1e-6) {
        return t;
      }
    }
    throw new AssertionError("never reaches " + delta);
  }
}
