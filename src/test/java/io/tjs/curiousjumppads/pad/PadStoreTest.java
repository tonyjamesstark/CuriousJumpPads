package io.tjs.curiousjumppads.pad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tjs.curiousjumppads.LaunchSpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PadStoreTest {
  private static final Logger LOG = Logger.getAnonymousLogger();
  private static final Pad FORWARD =
      new Pad(new LaunchSpec.Forward(1, 0, false), true, OptionalInt.empty());

  @TempDir Path dir;

  @BeforeAll
  static void quiet() {
    LOG.setUseParentHandlers(false);
  }

  private PadStore store(String name) {
    return new PadStore(dir.resolve(name).toFile(), LOG);
  }

  @Test
  void padsSurviveAReload() {
    PadStore store = store("pads.yml");
    BlockKey a = new BlockKey("world", 1, 100, -2);
    BlockKey b = new BlockKey("world_nether", 0, 64, 0);
    BlockKey c = new BlockKey("world", 5, 70, 5);
    store.put(
        a,
        new Pad(
            new LaunchSpec.To(new Vector(20.5, 100, -1.5), 0, true), true, OptionalInt.empty()));
    store.put(b, new Pad(new LaunchSpec.Forward(1.5, 0.6, false), false, OptionalInt.of(40)));
    store.put(
        c,
        new Pad(new LaunchSpec.Velocity(new Vector(0, 2, 1), true, true), true, OptionalInt.of(0)));
    store.setEnabled(false);

    PadStore reloaded = store("pads.yml");
    reloaded.load();

    assertEquals(store.all(), reloaded.all());
    assertFalse(reloaded.enabled());
  }

  @Test
  void removeAllReportsWhetherAPadWasThere() {
    PadStore store = store("pads.yml");
    BlockKey key = new BlockKey("world", 0, 0, 0);
    store.put(key, FORWARD);

    assertTrue(store.removeAll(List.of(key, key.below())));
    assertFalse(store.removeAll(List.of(key)));
  }

  @Test
  void missingFileLoadsEmptyAndEnabled() {
    PadStore store = store("absent.yml");
    store.load();
    assertTrue(store.all().isEmpty());
    assertTrue(store.enabled());
  }

  @Test
  void padUnderFindsAPlateInTheFeetBlockBeforeABlockBelow() {
    PadStore store = store("pads.yml");
    BlockKey feet = new BlockKey("world", 3, 100, 3);
    Pad plate = new Pad(new LaunchSpec.Forward(2, 0, false), true, OptionalInt.empty());
    store.put(feet.below(), FORWARD);
    assertEquals(FORWARD, store.padUnder(feet));
    store.put(feet, plate);
    assertEquals(plate, store.padUnder(feet));
    assertNull(store.padUnder(feet.below().below()));
  }

  @Test
  void badEntriesAreSkippedAndTheFileIsBackedUp() throws Exception {
    File file = dir.resolve("pads.yml").toFile();
    String yaml =
        """
        enabled: true
        pads:
        - {world: world, x: 1, y: 2, z: 3, launch: {type: forward, power: 1.0, lift: 0.0}}
        - {world: world, x: 1, y: 2, launch: {type: forward, power: 1.0, lift: 0.0}}
        - {world: world, x: 4, y: 2, z: 3, launch: {type: sideways}}
        - {world: world, x: 5, y: 2, z: 3, launch: {type: to, x: 1, y: 2}}
        - {world: world, x: 6, y: 2, z: 3, cooldown-ticks: soon, launch: {type: forward, power: 1, lift: 0}}
        - {world: world, x: 7, y: 2, z: 3}
        """;
    Files.writeString(file.toPath(), yaml);

    PadStore store = new PadStore(file, LOG);
    store.load();

    assertEquals(List.of(new BlockKey("world", 1, 2, 3)), List.copyOf(store.all().keySet()));
    assertEquals(yaml, Files.readString(dir.resolve("pads.yml.bak")));
  }

  @Test
  void unreadableFileLoadsEmptyAndIsBackedUp() throws Exception {
    File file = dir.resolve("pads.yml").toFile();
    Files.writeString(file.toPath(), "pads: [unclosed\n");

    PadStore store = new PadStore(file, LOG);
    store.load();

    assertTrue(store.all().isEmpty());
    assertTrue(Files.exists(dir.resolve("pads.yml.bak")));
  }
}
