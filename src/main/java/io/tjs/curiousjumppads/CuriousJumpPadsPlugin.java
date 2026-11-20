package io.tjs.curiousjumppads;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.tjs.curiousjumppads.pad.PadCommand;
import io.tjs.curiousjumppads.pad.PadListener;
import io.tjs.curiousjumppads.pad.PadStore;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point: reads {@code config.yml} and {@code pads.yml}, registers the listeners and
 * the {@code /launch} and {@code /jumppad} commands, and rereads both files on {@code /jumppad
 * reload}.
 */
public final class CuriousJumpPadsPlugin extends JavaPlugin {
  private Settings settings;
  private PadStore pads;

  /** Created by the server. */
  public CuriousJumpPadsPlugin() {}

  @Override
  public void onEnable() {
    saveDefaultConfig();
    settings = Settings.from(getConfig());
    FallGuard fallGuard = new FallGuard(() -> settings);
    MoveGuard moveGuard = new MoveGuard(() -> settings);
    Launcher launcher = new Launcher(fallGuard, moveGuard, () -> settings);
    pads = new PadStore(new File(getDataFolder(), "pads.yml"), getLogger());
    pads.load();

    getServer().getPluginManager().registerEvents(fallGuard, this);
    getServer().getPluginManager().registerEvents(moveGuard, this);
    getServer()
        .getPluginManager()
        .registerEvents(new PadListener(pads, launcher, () -> settings.padCooldownTicks()), this);
    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              event
                  .registrar()
                  .register(LaunchCommand.node(launcher), "Set or add to player velocity");
              event
                  .registrar()
                  .register(new PadCommand(pads, this::reload).node(), "Manage jump pads");
            });
  }

  private void reload() {
    reloadConfig();
    settings = Settings.from(getConfig());
    pads.load();
  }
}
