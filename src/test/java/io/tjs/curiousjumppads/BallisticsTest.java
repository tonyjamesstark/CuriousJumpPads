package io.tjs.curiousjumppads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.function.Supplier;
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
    Vector v0 = Ballistics.solve(delta, onGround, 0, CAP).orElseThrow();

    assertTrue(Ballistics.withinCap(v0, CAP), v0::toString);
    int ticks = Ballistics.landingTick(v0, onGround, 0, y);
    assertLandsOn(delta, v0, Vector::new, ticks, onGround, 0, 0);
    Ballistics.State end = Ballistics.simulate(v0, ticks, onGround, 0);
    assertTrue(end.apex() >= y + Ballistics.CLEARANCE, "arc does not clear the target");
  }

  @Test
  void airborneMatchesClosedForm() {
    Vector v0 = new Vector(2, 1.5, -1);
    int t = 37;
    Vector pos = Ballistics.simulate(v0, t, false, 0).position();

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
    assertTrue(Ballistics.solve(far, false, 0, CAP).isPresent());
    assertEquals(Optional.empty(), Ballistics.solve(far, true, 0, CAP));
  }

  @Test
  void tooFarForOneLaunch() {
    assertEquals(Optional.empty(), Ballistics.solve(new Vector(200, 0, 0), true, 0, CAP));
  }

  @Test
  void fixedFlightTimeIsHonoured() {
    Vector delta = new Vector(10, 0, 0);
    Vector v0 = Ballistics.solve(delta, 40, true, 0, CAP).orElseThrow();
    assertLandsOn(delta, v0, Vector::new, 40, true, 0, 0);
  }

  @Test
  void fixedFlightTimeOverTheCapIsUnreachable() {
    Vector delta = new Vector(40, 0, 0);
    assertEquals(Optional.empty(), Ballistics.solve(delta, 10, true, 0, CAP));
    assertTrue(Ballistics.solve(delta, 10, true, 0, 10).isPresent());
  }

  @Test
  void boostRestoresLaunchHorizontalSpeedEveryIntervalButTheLast() {
    Vector v0 = new Vector(2, 1, -1);
    // Boosted after tick 10; the boost after tick 20 would be the last before landing.
    Vector at = Ballistics.simulate(v0, 21, false, 10).velocity();
    assertEquals(2 * Math.pow(0.91, 11), at.getX(), 1e-9);
    assertEquals(-Math.pow(0.91, 11), at.getZ(), 1e-9);
    assertEquals(Ballistics.simulate(v0, 21, false, 0).velocity().getY(), at.getY(), 1e-9);
    assertEquals(
        2 * Math.pow(0.91, 11), Ballistics.simulate(v0, 11, false, 0).velocity().getX(), 1e-9);
  }

  @ParameterizedTest
  @CsvSource({"80, 0, 0, true, 10", "60, 10, 30, true, 5", "100, -20, 0, false, 20"})
  void boostedArcLandsPastUnboostedReach(
      double x, double y, double z, boolean onGround, int interval) {
    Vector delta = new Vector(x, y, z);
    assertEquals(Optional.empty(), Ballistics.solve(delta, onGround, 0, CAP));
    Vector v0 = Ballistics.solve(delta, onGround, interval, CAP).orElseThrow();

    assertTrue(Ballistics.withinCap(v0, CAP), v0::toString);
    int ticks = Ballistics.landingTick(v0, onGround, interval, y);
    assertLandsOn(delta, v0, Vector::new, ticks, onGround, interval, 0);
    Ballistics.State end = Ballistics.simulate(v0, ticks, onGround, interval);
    assertTrue(end.apex() >= y + Ballistics.CLEARANCE, "arc does not clear the target");
  }

  @Test
  void landingTickIsTheSolvedFlightTime() {
    Vector delta = new Vector(60, 5, 20);
    Vector v0 = Ballistics.solve(delta, 50, true, 10, CAP).orElseThrow();
    assertEquals(50, Ballistics.landingTick(v0, true, 10, delta.getY()));
  }

  @Test
  void landingTickEndsEveryLaunch() {
    // set/forward launches have no target; they land when they come back to the start height.
    int t = Ballistics.landingTick(new Vector(2, 1.5, 0), false, 5, 0);
    assertTrue(t < Ballistics.MAX_TICKS, () -> "landing tick " + t);
    assertTrue(Ballistics.simulate(new Vector(2, 1.5, 0), t - 1, false, 5).position().getY() > 0);
    int straightUp = Ballistics.landingTick(new Vector(0, CAP, 0), true, 10, 0);
    assertTrue(straightUp > 70 && straightUp < 90, () -> "landing tick " + straightUp);
  }

  @Test
  void arcEndsHalfItsLastStepBelowTheTarget() {
    // A late boost that resets vertical speed raises the arc a little; it must still land on time.
    Vector delta = new Vector(60, 0, 0);
    Vector v0 = Ballistics.solve(delta, 32, true, 10, CAP).orElseThrow();
    double lastStep = Ballistics.simulate(v0, 31, true, 10).velocity().getY();
    double end = Ballistics.simulate(v0, 32, true, 10).position().getY();
    assertEquals(lastStep / 2, end, 1e-2);
  }

  // The flight is over the target's floor one tick before arriving and under it on arrival, by at
  // least a third of the last step either way, and right above or below the target.
  private static void assertLandsOn(
      Vector delta,
      Vector v0,
      Supplier<Vector> start,
      int ticks,
      boolean onGround,
      int interval,
      int phase) {
    Vector before =
        Ballistics.simulate(v0, ticks - 1, onGround, interval, phase).position().add(start.get());
    Ballistics.State end = Ballistics.simulate(v0, ticks, onGround, interval, phase);
    Vector at = end.position().add(start.get());
    double step = before.getY() - at.getY();
    assertTrue(step > 0, "still rising on arrival");
    assertTrue(before.getY() - delta.getY() > step / 3, () -> "over the floor by " + before);
    assertTrue(delta.getY() - at.getY() > step / 3, () -> "under the floor by " + at);
    assertEquals(delta.getX(), at.getX(), 1e-6);
    assertEquals(delta.getZ(), at.getZ(), 1e-6);
  }
}
