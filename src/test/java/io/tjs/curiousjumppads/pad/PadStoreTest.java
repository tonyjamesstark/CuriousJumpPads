package io.tjs.curiousjumppads.pad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tjs.curiousjumppads.LaunchSpec;
import java.io.File;
import java.nio.file.Path;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PadStoreTest {
  @TempDir Path dir;

  @Test
  void padsSurviveAReload() throws Exception {
    File file = dir.resolve("pads.yml").toFile();
    PadStore store = new PadStore(file);
    BlockKey a = new BlockKey("world", 1, 100, -2);
    BlockKey b = new BlockKey("world_nether", 0, 64, 0);
    BlockKey c = new BlockKey("world", 5, 70, 5);
    store.put(a, new Pad(new LaunchSpec.To(new Vector(20.5, 100, -1.5), 0), true));
    store.put(b, new Pad(new LaunchSpec.Forward(1.5, 0.6), false));
    store.put(c, new Pad(new LaunchSpec.Velocity(new Vector(0, 2, 1), true), true));
    store.setEnabled(false);

    PadStore reloaded = new PadStore(file);
    reloaded.load();

    assertEquals(store.all(), reloaded.all());
    assertFalse(reloaded.enabled());
  }

  @Test
  void removeReportsWhetherAPadWasThere() throws Exception {
    PadStore store = new PadStore(dir.resolve("pads.yml").toFile());
    BlockKey key = new BlockKey("world", 0, 0, 0);
    store.put(key, new Pad(new LaunchSpec.Forward(1, 0), true));

    assertTrue(store.remove(key));
    assertFalse(store.remove(key));
  }

  @Test
  void missingFileLoadsEmptyAndEnabled() {
    PadStore store = new PadStore(dir.resolve("absent.yml").toFile());
    store.load();
    assertTrue(store.all().isEmpty());
    assertTrue(store.enabled());
  }
}
