package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.LaunchSpec;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** Pads by block, saved to pads.yml on every change. */
public final class PadStore {
  private final File file;
  private final Map<BlockKey, Pad> pads = new LinkedHashMap<>();
  private boolean enabled = true;

  /**
   * Creates a store backed by a file.
   *
   * @param file {@code pads.yml}; need not exist yet
   */
  public PadStore(File file) {
    this.file = file;
  }

  /** Replaces the pads in memory with those in the file; a missing file means no pads. */
  public void load() {
    pads.clear();
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    enabled = yaml.getBoolean("enabled", true);
    for (Map<?, ?> m : yaml.getMapList("pads")) {
      BlockKey key =
          new BlockKey(
              String.valueOf(m.get("world")),
              ((Number) m.get("x")).intValue(),
              ((Number) m.get("y")).intValue(),
              ((Number) m.get("z")).intValue());
      pads.put(
          key,
          new Pad(
              LaunchSpec.deserialize((Map<?, ?>) m.get("launch")),
              !Boolean.FALSE.equals(m.get("enabled"))));
    }
  }

  /**
   * Writes every pad and the global switch to the file.
   *
   * @throws IOException if the file cannot be written
   */
  public void save() throws IOException {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("enabled", enabled);
    List<Map<String, Object>> list = new ArrayList<>();
    pads.forEach(
        (key, pad) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("world", key.world());
          m.put("x", key.x());
          m.put("y", key.y());
          m.put("z", key.z());
          m.put("enabled", pad.enabled());
          m.put("launch", pad.spec().serialize());
          list.add(m);
        });
    yaml.set("pads", list);
    yaml.save(file);
  }

  /**
   * The pad at a block.
   *
   * @param key a block
   * @return the pad at that block, or null
   */
  public Pad get(BlockKey key) {
    return pads.get(key);
  }

  /**
   * Every pad.
   *
   * @return every pad, in the order they were added; read-only
   */
  public Map<BlockKey, Pad> all() {
    return Collections.unmodifiableMap(pads);
  }

  /**
   * Adds or replaces a pad and saves.
   *
   * @param key the pad block
   * @param pad the pad
   * @throws IOException if the file cannot be written
   */
  public void put(BlockKey key, Pad pad) throws IOException {
    pads.put(key, pad);
    save();
  }

  /**
   * Removes a pad and saves, if there is one.
   *
   * @param key the pad block
   * @return whether a pad was removed
   * @throws IOException if the file cannot be written
   */
  public boolean remove(BlockKey key) throws IOException {
    if (pads.remove(key) == null) {
      return false;
    }
    save();
    return true;
  }

  /**
   * Whether pads are on.
   *
   * @return the global switch; when false no pad launches
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Sets the global switch ({@code /jumppad enable|disable all}) and saves.
   *
   * @param enabled the new state
   * @throws IOException if the file cannot be written
   */
  public void setEnabled(boolean enabled) throws IOException {
    this.enabled = enabled;
    save();
  }
}
