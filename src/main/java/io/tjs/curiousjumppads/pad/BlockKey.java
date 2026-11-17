package io.tjs.curiousjumppads.pad;

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

  @Override
  public String toString() {
    return world + " " + x + " " + y + " " + z;
  }
}
