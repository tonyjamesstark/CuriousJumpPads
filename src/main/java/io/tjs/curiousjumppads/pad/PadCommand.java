package io.tjs.curiousjumppads.pad;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.tjs.curiousjumppads.LaunchSpec;
import io.tjs.curiousjumppads.SpecArguments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /jumppad: pads on the block the player looks at, plus list and the switch for every pad. */
public final class PadCommand {
  private static final int REACH = 5;
  private static final SimpleCommandExceptionType NO_BLOCK =
      error("Look at a block within 5 blocks.");
  private static final SimpleCommandExceptionType NOT_A_PAD =
      error("That block is not a jump pad.");
  private static final SimpleCommandExceptionType PLAYERS_ONLY = error("Only players can do that.");

  private final PadStore store;

  /**
   * Creates the command.
   *
   * @param store the pads the command edits
   */
  public PadCommand(PadStore store) {
    this.store = store;
  }

  /**
   * Builds the command tree.
   *
   * @return the {@code /jumppad} node, gated on {@code jumppads.admin}
   */
  public LiteralCommandNode<CommandSourceStack> node() {
    return Commands.literal("jumppad")
        .requires(source -> source.getSender().hasPermission("jumppads.admin"))
        .then(SpecArguments.addTo(Commands.literal("set"), this::set))
        .then(Commands.literal("remove").executes(this::remove))
        .then(Commands.literal("info").executes(this::info))
        .then(
            Commands.literal("list")
                .executes(c -> list(c, null))
                .then(
                    Commands.argument("world", word())
                        .suggests(
                            (c, b) -> {
                              Bukkit.getWorlds().forEach(w -> b.suggest(w.getName()));
                              return b.buildFuture();
                            })
                        .executes(c -> list(c, getString(c, "world")))))
        .then(
            Commands.literal("cooldown")
                .then(
                    Commands.argument("ticks", integer(0))
                        .executes(c -> cooldown(c, OptionalInt.of(getInteger(c, "ticks")))))
                .then(Commands.literal("default").executes(c -> cooldown(c, OptionalInt.empty()))))
        .then(
            Commands.literal("enable")
                .executes(c -> toggle(c, true))
                .then(Commands.literal("all").executes(c -> toggleAll(c, true))))
        .then(
            Commands.literal("disable")
                .executes(c -> toggle(c, false))
                .then(Commands.literal("all").executes(c -> toggleAll(c, false))))
        .build();
  }

  private int set(CommandContext<CommandSourceStack> c, LaunchSpec spec)
      throws CommandSyntaxException {
    BlockKey key = BlockKey.of(lookedAt(c));
    Pad old = store.get(key);
    store.put(key, new Pad(spec, true, old == null ? OptionalInt.empty() : old.cooldownTicks()));
    reply(c, "Jump pad at " + key + ": " + spec.describe());
    return Command.SINGLE_SUCCESS;
  }

  private int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    BlockKey key = padKey(c);
    store.removeAll(List.of(key));
    reply(c, "Removed the jump pad at " + key + ".");
    return Command.SINGLE_SUCCESS;
  }

  private int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    BlockKey key = padKey(c);
    reply(c, key + ": " + store.get(key).describe());
    return Command.SINGLE_SUCCESS;
  }

  private int list(CommandContext<CommandSourceStack> c, String world) {
    Map<BlockKey, Pad> pads = new LinkedHashMap<>(store.all());
    if (world != null) {
      pads.keySet().removeIf(key -> !key.world().equals(world));
    }
    CommandSender sender = c.getSource().getSender();
    sender.sendMessage(
        pads.size()
            + " jump pads"
            + (world == null ? "" : " in " + world)
            + (store.enabled() ? "" : ", all disabled")
            + ":");
    pads.forEach((key, pad) -> sender.sendMessage(" " + key + ": " + pad.describe()));
    return pads.size();
  }

  private int cooldown(CommandContext<CommandSourceStack> c, OptionalInt ticks)
      throws CommandSyntaxException {
    BlockKey key = padKey(c);
    store.put(key, store.get(key).withCooldown(ticks));
    reply(
        c,
        "Jump pad at "
            + key
            + (ticks.isPresent()
                ? ": cooldown " + ticks.getAsInt() + " ticks."
                : ": cooldown back to pad-cooldown-ticks."));
    return Command.SINGLE_SUCCESS;
  }

  private int toggle(CommandContext<CommandSourceStack> c, boolean enabled)
      throws CommandSyntaxException {
    BlockKey key = padKey(c);
    store.put(key, store.get(key).withEnabled(enabled));
    reply(c, "Jump pad at " + key + (enabled ? " enabled." : " disabled."));
    return Command.SINGLE_SUCCESS;
  }

  private int toggleAll(CommandContext<CommandSourceStack> c, boolean enabled) {
    store.setEnabled(enabled);
    reply(c, enabled ? "All jump pads enabled." : "All jump pads disabled.");
    return Command.SINGLE_SUCCESS;
  }

  private BlockKey padKey(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    BlockKey key = BlockKey.of(lookedAt(c));
    if (store.get(key) == null) {
      throw NOT_A_PAD.create();
    }
    return key;
  }

  private static Block lookedAt(CommandContext<CommandSourceStack> c)
      throws CommandSyntaxException {
    if (!(c.getSource().getExecutor() instanceof Player player)) {
      throw PLAYERS_ONLY.create();
    }
    Block block = player.getTargetBlockExact(REACH);
    if (block == null) {
      throw NO_BLOCK.create();
    }
    return block;
  }

  private static void reply(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().getSender().sendMessage(text);
  }

  private static SimpleCommandExceptionType error(String text) {
    return new SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(Component.text(text)));
  }
}
