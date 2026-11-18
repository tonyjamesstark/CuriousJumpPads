package io.tjs.curiousjumppads.pad;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.block.Block;

/**
 * A block position that survives chunk unloads and restarts.
 *
 * @param world world name
 * @param x block x
 * @param y block y
 * @param z block z
 */
public record BlockKey(String world, int x, int y, int z) {
  /**
   * The key of a block.
   *
   * @param block a block
   * @return its key
   */
  public static BlockKey of(Block block) {
    return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
  }

  /**
   * The block one down.
   *
   * @return the key of the block below this one
   */
  public BlockKey below() {
    return new BlockKey(world, x, y - 1, z);
  }

  /**
   * The key as plain values, for {@code pads.yml}.
   *
   * @return a map with {@code world}, {@code x}, {@code y} and {@code z}; read back by {@link
   *     #read}
   */
  Map<String, Object> write() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("world", world);
    m.put("x", x);
    m.put("y", y);
    m.put("z", z);
    return m;
  }

  /**
   * Reads a key written by {@link #write}.
   *
   * @param m a pad's map from {@code pads.yml}
   * @return the key
   * @throws IllegalArgumentException if the world or a coordinate is missing
   */
  static BlockKey read(Map<?, ?> m) {
    Object world = m.get("world");
    if (world == null) {
      throw new IllegalArgumentException("no world");
    }
    return new BlockKey(
        String.valueOf(world), coordinate(m, "x"), coordinate(m, "y"), coordinate(m, "z"));
  }

  private static int coordinate(Map<?, ?> m, String key) {
    if (m.get(key) instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException(key + " is not a number");
  }

  @Override
  public String toString() {
    return world + " " + x + " " + y + " " + z;
  }
}
