package io.tjs.curiousjumppads.pad;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import net.kyori.adventure.text.Component;
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
        .then(Commands.literal("list").executes(this::list))
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
    save(() -> store.put(key, new Pad(spec, true)));
    reply(c, "Jump pad at " + key + ": " + spec.describe());
    return Command.SINGLE_SUCCESS;
  }

  private int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    BlockKey key = padKey(c);
    save(() -> store.remove(key));
    reply(c, "Removed the jump pad at " + key + ".");
    return Command.SINGLE_SUCCESS;
  }

  private int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
    BlockKey key = padKey(c);
    Pad pad = store.get(key);
    reply(c, key + ": " + pad.spec().describe() + (pad.enabled() ? "" : " (disabled)"));
    return Command.SINGLE_SUCCESS;
  }

  private int list(CommandContext<CommandSourceStack> c) {
    CommandSender sender = c.getSource().getSender();
    sender.sendMessage(
        store.all().size() + " jump pads" + (store.enabled() ? "" : ", all disabled") + ":");
    store
        .all()
        .forEach(
            (key, pad) ->
                sender.sendMessage(
                    " "
                        + key
                        + ": "
                        + pad.spec().describe()
                        + (pad.enabled() ? "" : " (disabled)")));
    return store.all().size();
  }

  private int toggle(CommandContext<CommandSourceStack> c, boolean enabled)
      throws CommandSyntaxException {
    BlockKey key = padKey(c);
    save(() -> store.put(key, store.get(key).withEnabled(enabled)));
    reply(c, "Jump pad at " + key + (enabled ? " enabled." : " disabled."));
    return Command.SINGLE_SUCCESS;
  }

  private int toggleAll(CommandContext<CommandSourceStack> c, boolean enabled) {
    save(() -> store.setEnabled(enabled));
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

  private interface IoAction {
    void run() throws IOException;
  }

  private static void save(IoAction action) {
    try {
      action.run();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not save pads.yml", e);
    }
  }

  private static void reply(CommandContext<CommandSourceStack> c, String text) {
    c.getSource().getSender().sendMessage(text);
  }

  private static SimpleCommandExceptionType error(String text) {
    return new SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(Component.text(text)));
  }
}
