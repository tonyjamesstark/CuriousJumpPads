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
            getConfig().getBoolean("cancel-fall-damage.enabled"),
            getConfig().getLong("cancel-fall-damage.timeout-seconds") * 1000);
    MoveGuard moveGuard = new MoveGuard(getConfig().getBoolean("allow-fast-launches"));
    Launcher launcher = new Launcher(fallGuard, moveGuard, getConfig().getDouble("max-velocity"));
    PadStore pads = new PadStore(new File(getDataFolder(), "pads.yml"), getLogger());
    pads.load();

    getServer().getPluginManager().registerEvents(fallGuard, this);
    getServer().getPluginManager().registerEvents(moveGuard, this);
    getServer()
        .getPluginManager()
        .registerEvents(
            new PadListener(pads, launcher, getConfig().getInt("pad-cooldown-ticks")), this);
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
