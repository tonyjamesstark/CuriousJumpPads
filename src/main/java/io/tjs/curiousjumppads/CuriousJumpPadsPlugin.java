package io.tjs.curiousjumppads;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.tjs.curiousjumppads.pad.PadCommand;
import io.tjs.curiousjumppads.pad.PadListener;
import io.tjs.curiousjumppads.pad.PadStore;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point: reads {@code config.yml} and {@code pads.yml}, registers the listeners and
 * the {@code /launch} and {@code /jumppad} commands.
 */
public final class CuriousJumpPadsPlugin extends JavaPlugin {
  /** Created by the server. */
  public CuriousJumpPadsPlugin() {}

  @Override
  public void onEnable() {
    saveDefaultConfig();
    FallGuard fallGuard =
        new FallGuard(
            getConfig().getBoolean("cancel-fall-damage"),
            getConfig().getLong("fall-guard-seconds") * 1000);
    Launcher launcher = new Launcher(fallGuard, getConfig().getDouble("max-velocity"));
    PadStore pads = new PadStore(new File(getDataFolder(), "pads.yml"));
    pads.load();

    getServer().getPluginManager().registerEvents(fallGuard, this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new PadListener(pads, launcher, getConfig().getInt("pad-cooldown-ticks"), getLogger()),
            this);
    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              event
                  .registrar()
                  .register(LaunchCommand.node(launcher), "Set or add to player velocity");
              event.registrar().register(new PadCommand(pads).node(), "Manage jump pads");
            });
  }
}
