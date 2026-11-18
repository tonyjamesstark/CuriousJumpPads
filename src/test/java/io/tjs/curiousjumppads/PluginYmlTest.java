package io.tjs.curiousjumppads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginYmlTest {
  // '/' as the path separator, since permission names contain dots.
  private static YamlConfiguration resource(String name) throws Exception {
    YamlConfiguration yml = new YamlConfiguration();
    yml.options().pathSeparator('/');
    yml.load(
        new InputStreamReader(
            PluginYmlTest.class.getClassLoader().getResourceAsStream(name),
            StandardCharsets.UTF_8));
    return yml;
  }

  @Test
  void pointsAtThePluginWithAFilteredVersion() throws Exception {
    YamlConfiguration yml = resource("plugin.yml");
    assertEquals(CuriousJumpPadsPlugin.class.getName(), yml.getString("main"));
    assertFalse(yml.getString("version").contains("${"), yml.getString("version"));
    assertEquals("26.2", yml.getString("api-version"));
  }

  @Test
  void parentPermissionGrantsEveryOther() throws Exception {
    ConfigurationSection perms = resource("plugin.yml").getConfigurationSection("permissions");
    Set<String> others = perms.getKeys(false);
    others.remove("jumppads.*");
    assertEquals(Set.of("jumppads.launch", "jumppads.admin", "jumppads.use"), others);
    assertEquals(others, perms.getConfigurationSection("jumppads.*/children").getKeys(false));
  }

  @Test
  void configHasEveryKeyThePluginReads() throws Exception {
    YamlConfiguration config = resource("config.yml");
    for (String key :
        Set.of(
            "max-velocity",
            "allow-fast-launches",
            "cancel-fall-damage/enabled",
            "cancel-fall-damage/timeout-seconds",
            "pad-cooldown-ticks")) {
      assertNotNull(config.get(key), key);
    }
  }
}
