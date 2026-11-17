package io.tjs.curiousjumppads;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The {@code /launch} command: set, add or aim player velocity. Registered through Brigadier, so
 * selectors and relative coordinates work from players, the console and command blocks.
 */
final class LaunchCommand {
  private LaunchCommand() {}

  /**
   * Builds the command tree.
   *
   * @param launcher applies the velocity and arms the fall guard
   * @return the {@code /launch} node, gated on {@code jumppads.launch}
   */
  static LiteralCommandNode<CommandSourceStack> node(Launcher launcher) {
    var targets =
        Commands.argument("targets", ArgumentTypes.players())
            .then(
                Commands.literal("toward")
                    .then(
                        Commands.argument("entity", ArgumentTypes.entity())
                            .executes(c -> toward(c, launcher, 0))
                            .then(
                                Commands.argument("ticks", integer(1, Ballistics.MAX_TICKS))
                                    .executes(c -> toward(c, launcher, getInteger(c, "ticks"))))));
    return Commands.literal("launch")
        .requires(source -> source.getSender().hasPermission("jumppads.launch"))
        .then(SpecArguments.addTo(targets, (c, spec) -> run(c, launcher, spec)))
        .build();
  }

  private static int toward(CommandContext<CommandSourceStack> c, Launcher launcher, int ticks)
      throws CommandSyntaxException {
    Entity entity =
        c.getArgument("entity", EntitySelectorArgumentResolver.class)
            .resolve(c.getSource())
            .getFirst();
    return run(c, launcher, new LaunchSpec.To(entity.getLocation().toVector(), ticks));
  }

  private static int run(CommandContext<CommandSourceStack> c, Launcher launcher, LaunchSpec spec)
      throws CommandSyntaxException {
    List<Player> targets =
        c.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(c.getSource());
    int launched = 0;
    for (Player p : targets) {
      if (launcher.launch(p, spec)) {
        launched++;
      } else if (!p.isGliding() && !p.isFlying()) {
        c.getSource().getSender().sendMessage(unreachable(spec, launcher.maxVelocity()));
      }
    }
    return launched;
  }

  /**
   * The error shown when no arc reaches a {@link LaunchSpec.To} target.
   *
   * @param spec the launch that failed
   * @param maxVelocity the configured cap, quoted in the message
   * @return a red message saying why
   */
  static Component unreachable(LaunchSpec spec, double maxVelocity) {
    String text =
        spec instanceof LaunchSpec.To t && t.ticks() > 0
            ? "No arc reaches that point in exactly " + t.ticks() + " ticks."
            : "Too far for one launch at max-velocity "
                + maxVelocity
                + "; chain pads or raise max-velocity.";
    return Component.text(text, NamedTextColor.RED);
  }
}
