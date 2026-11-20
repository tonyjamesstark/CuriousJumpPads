package io.tjs.curiousjumppads;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import org.bukkit.util.Vector;

/** The {@code set|add|forward|to ... [boost]} arguments that both /launch and /jumppad set take. */
public final class SpecArguments {
  /** Runs a command once its launch arguments are parsed. */
  public interface Executor {
    /**
     * Runs the command.
     *
     * @param c the command context
     * @param spec the launch the arguments describe
     * @return the Brigadier result, such as the number of players launched
     * @throws CommandSyntaxException if a selector or argument cannot be resolved
     */
    int run(CommandContext<CommandSourceStack> c, LaunchSpec spec) throws CommandSyntaxException;
  }

  /** The optional last word of every launch form, see {@link LaunchSpec#boost}. */
  public static final String BOOST = "boost";

  private interface Spec {
    LaunchSpec make(CommandContext<CommandSourceStack> c, boolean boost)
        throws CommandSyntaxException;
  }

  private SpecArguments() {}

  /**
   * Adds the {@code set}, {@code add}, {@code forward} and {@code to} branches under a node, each
   * with an optional trailing {@code boost}.
   *
   * @param <T> the node's builder type
   * @param node the node to extend, such as {@code /launch <targets>} or {@code /jumppad set}
   * @param exec what to run with the parsed launch
   * @return {@code node}, for chaining
   */
  public static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTo(T node, Executor exec) {
    return node.then(Commands.literal("set").then(vector(exec, false)))
        .then(Commands.literal("add").then(vector(exec, true)))
        .then(
            Commands.literal("forward")
                .then(
                    end(
                        Commands.argument("power", doubleArg(0))
                            .then(
                                end(
                                    Commands.argument("lift", doubleArg()),
                                    exec,
                                    (c, boost) ->
                                        new LaunchSpec.Forward(
                                            getDouble(c, "power"), getDouble(c, "lift"), boost))),
                        exec,
                        (c, boost) -> new LaunchSpec.Forward(getDouble(c, "power"), 0, boost))))
        .then(
            Commands.literal("to")
                .then(
                    end(
                        Commands.argument("position", ArgumentTypes.finePosition(true))
                            .then(
                                end(
                                    Commands.argument("ticks", integer(1, Ballistics.MAX_TICKS)),
                                    exec,
                                    (c, boost) ->
                                        new LaunchSpec.To(
                                            target(c), getInteger(c, "ticks"), boost))),
                        exec,
                        (c, boost) -> new LaunchSpec.To(target(c), 0, boost))));
  }

  private static ArgumentBuilder<CommandSourceStack, ?> vector(Executor exec, boolean add) {
    return Commands.argument("x", doubleArg())
        .then(
            Commands.argument("y", doubleArg())
                .then(
                    end(
                        Commands.argument("z", doubleArg()),
                        exec,
                        (c, boost) ->
                            new LaunchSpec.Velocity(
                                new Vector(getDouble(c, "x"), getDouble(c, "y"), getDouble(c, "z")),
                                add,
                                boost))));
  }

  // Makes the node executable as it is and with a trailing `boost`.
  private static <T extends ArgumentBuilder<CommandSourceStack, T>> T end(
      T node, Executor exec, Spec spec) {
    return node.executes(c -> exec.run(c, spec.make(c, false)))
        .then(Commands.literal(BOOST).executes(c -> exec.run(c, spec.make(c, true))));
  }

  // The player lands in the target block: its y is rounded down to the block's floor, so a
  // command block's centred ~ coordinates mean the block above, not half a block over the floor.
  private static Vector target(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    FinePosition p = c.getArgument("position", FinePositionResolver.class).resolve(c.getSource());
    return new Vector(p.x(), Math.floor(p.y()), p.z());
  }
}
