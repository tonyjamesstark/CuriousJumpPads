package io.tjs.curiousjumppads;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.util.Vector;

/** How to launch a player, shared by /launch and pads. */
public sealed interface LaunchSpec {
  /**
   * Set the velocity, or add it to the current one.
   *
   * @param velocity blocks per tick
   * @param add true to add to the current velocity
   */
  record Velocity(Vector velocity, boolean add) implements LaunchSpec {}

  /**
   * Along the player's look direction, plus {@code lift} upward.
   *
   * @param power multiplier on the unit look vector, in blocks per tick
   * @param lift added to y, in blocks per tick
   */
  record Forward(double power, double lift) implements LaunchSpec {}

  /**
   * An arc that lands at {@code target}.
   *
   * @param target landing position; y is the floor the player lands on
   * @param ticks flight time, or 0 for the flattest arc under the cap
   */
  record To(Vector target, int ticks) implements LaunchSpec {}

  /**
   * The launch as plain values, for {@code pads.yml}.
   *
   * @return a map with {@code type} and that type's fields; read back by {@link #deserialize}
   */
  default Map<String, Object> serialize() {
    Map<String, Object> m = new LinkedHashMap<>();
    switch (this) {
      case Velocity v -> {
        m.put("type", v.add() ? "add" : "set");
        putVector(m, v.velocity());
      }
      case Forward f -> {
        m.put("type", "forward");
        m.put("power", f.power());
        m.put("lift", f.lift());
      }
      case To t -> {
        m.put("type", "to");
        putVector(m, t.target());
        m.put("ticks", t.ticks());
      }
    }
    return m;
  }

  /**
   * Reads a launch written by {@link #serialize}.
   *
   * @param m the map from {@code pads.yml}
   * @return the launch
   * @throws IllegalArgumentException if {@code type} is not set, add, forward or to, or a field is
   *     missing
   */
  static LaunchSpec deserialize(Map<?, ?> m) {
    String type = String.valueOf(m.get("type"));
    return switch (type) {
      case "set", "add" -> new Velocity(vector(m), type.equals("add"));
      case "forward" -> new Forward(number(m, "power"), number(m, "lift"));
      case "to" -> new To(vector(m), (int) number(m, "ticks"));
      default -> throw new IllegalArgumentException("unknown launch type: " + type);
    };
  }

  /**
   * A one-line summary for chat, such as {@code to 20.50 100.00 0.50 in 40 ticks}.
   *
   * @return the summary
   */
  default String describe() {
    return switch (this) {
      case Velocity v -> (v.add() ? "add " : "set ") + format(v.velocity());
      case Forward f -> "forward " + f.power() + " lift " + f.lift();
      case To t ->
          "to " + format(t.target()) + (t.ticks() > 0 ? " in " + t.ticks() + " ticks" : "");
    };
  }

  private static void putVector(Map<String, Object> m, Vector v) {
    m.put("x", v.getX());
    m.put("y", v.getY());
    m.put("z", v.getZ());
  }

  private static Vector vector(Map<?, ?> m) {
    return new Vector(number(m, "x"), number(m, "y"), number(m, "z"));
  }

  private static double number(Map<?, ?> m, String key) {
    if (m.get(key) instanceof Number n) {
      return n.doubleValue();
    }
    throw new IllegalArgumentException(key + " is not a number");
  }

  private static String format(Vector v) {
    return String.format("%.2f %.2f %.2f", v.getX(), v.getY(), v.getZ());
  }
}
