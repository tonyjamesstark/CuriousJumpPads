package io.tjs.curiousjumppads.pad;

import io.tjs.curiousjumppads.LaunchSpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Pads by block, saved to pads.yml on every change. A failed save is logged and the pads stay in
 * memory, so the next change saves them.
 */
public final class PadStore {
  private final File file;
  private final Logger log;
  private final Map<BlockKey, Pad> pads = new LinkedHashMap<>();
  private boolean enabled = true;

  /**
   * Creates a store backed by a file.
   *
   * @param file {@code pads.yml}; need not exist yet
   * @param log where read and save failures go
   */
  public PadStore(File file, Logger log) {
    this.file = file;
    this.log = log;
  }

  /**
   * Replaces the pads in memory with those in the file; a missing file means no pads. An unreadable
   * file, or an entry that is not a valid pad, is logged and skipped, and the file is copied to
   * {@code pads.yml.bak} before the next save overwrites it.
   */
  public void load() {
    pads.clear();
    enabled = true;
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.load(file);
    } catch (IOException | InvalidConfigurationException e) {
      log.log(Level.SEVERE, "Could not read " + file.getName() + "; starting with no pads", e);
      backUp();
      return;
    }
    enabled = yaml.getBoolean("enabled", true);
    boolean skipped = false;
    for (Map<?, ?> m : yaml.getMapList("pads")) {
      try {
        pads.put(BlockKey.read(m), read(m));
      } catch (IllegalArgumentException e) {
        log.warning("Skipped a pad in " + file.getName() + " (" + e.getMessage() + "): " + m);
        skipped = true;
      }
    }
    if (skipped) {
      backUp();
    }
  }

  private static Pad read(Map<?, ?> m) {
    if (!(m.get("launch") instanceof Map<?, ?> launch)) {
      throw new IllegalArgumentException("no launch");
    }
    Object cooldown = m.get("cooldown-ticks");
    if (cooldown != null && !(cooldown instanceof Number)) {
      throw new IllegalArgumentException("cooldown-ticks is not a number");
    }
    return new Pad(
        LaunchSpec.deserialize(launch),
        !Boolean.FALSE.equals(m.get("enabled")),
        cooldown == null ? OptionalInt.empty() : OptionalInt.of(((Number) cooldown).intValue()));
  }

  private void backUp() {
    File backup = new File(file.getParentFile(), file.getName() + ".bak");
    try {
      Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      log.warning("Saved the original as " + backup.getName());
    } catch (IOException e) {
      log.log(Level.SEVERE, "Could not back up " + file.getName(), e);
    }
  }

  private void save() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("enabled", enabled);
    List<Map<String, Object>> list = new ArrayList<>();
    pads.forEach(
        (key, pad) -> {
          Map<String, Object> m = key.write();
          m.put("enabled", pad.enabled());
          pad.cooldownTicks().ifPresent(t -> m.put("cooldown-ticks", t));
          m.put("launch", pad.spec().serialize());
          list.add(m);
        });
    yaml.set("pads", list);
    try {
      yaml.save(file);
    } catch (IOException e) {
      log.log(Level.SEVERE, "Could not save " + file.getName() + "; pads are kept in memory", e);
    }
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
   * The pad a player whose feet are in a block stands on: a plate pad in that block, else a full
   * block pad under it.
   *
   * @param feet the block the player's feet are in
   * @return the pad, or null
   */
  public Pad padUnder(BlockKey feet) {
    Pad pad = pads.get(feet);
    return pad != null ? pad : pads.get(feet.below());
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
   */
  public void put(BlockKey key, Pad pad) {
    pads.put(key, pad);
    save();
  }

  /**
   * Removes the pads at some blocks and saves once, if any were there.
   *
   * @param keys the blocks
   * @return whether a pad was removed
   */
  public boolean removeAll(Collection<BlockKey> keys) {
    if (!pads.keySet().removeAll(keys)) {
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
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    save();
  }
}
