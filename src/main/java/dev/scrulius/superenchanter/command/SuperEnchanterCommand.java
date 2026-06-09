/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.scrulius.superenchanter.SuperEnchanterPlugin;
import dev.scrulius.superenchanter.util.EnchantmentHelper;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds the modern Brigadier command tree for {@code /superenchanter} ({@code /se}).
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code reload} — reload config + messages.</li>
 *   <li>{@code give <item> [player]} — give a configured MythicMobs library or
 *       potentiator item.</li>
 *   <li>{@code book <enchant> <level> [player]} — give an enchanted book.</li>
 *   <li>{@code bookshelf} — inspect the enchanted-library marks around you.</li>
 *   <li>{@code audit [lines] [player]} — read the tail of {@code audit.log}.</li>
 * </ul>
 */
public final class SuperEnchanterCommand {

    private static final String PERM_RELOAD = "superenchanter.reload";
    private static final String PERM_ADMIN = "superenchanter.admin";

    private final SuperEnchanterPlugin plugin;

    public SuperEnchanterCommand(@NotNull SuperEnchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Builds the full command node, ready to register with the Commands registrar. */
    @NotNull
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("superenchanter")
                .executes(this::info)
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(PERM_RELOAD))
                        .executes(this::reload))
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission(PERM_ADMIN))
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    final String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                    final TreeSet<String> ids = new TreeSet<>();
                                    ids.addAll(plugin.getPluginConfig().getEnchantedBookshelfIds());
                                    ids.addAll(plugin.getPluginConfig().getSuccessBoosterIds());
                                    ids.addAll(plugin.getPluginConfig().getCurseRemovalSealIds());
                                    ids.stream().filter(s -> s.startsWith(rem)).forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> give(ctx, null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(this::suggestOnline)
                                        .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("book")
                        .requires(src -> src.getSender().hasPermission(PERM_ADMIN))
                        .then(Commands.argument("enchant", StringArgumentType.word())
                                .suggests(this::suggestEnchants)
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 255))
                                        .executes(ctx -> book(ctx, null))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(this::suggestOnline)
                                                .executes(ctx -> book(ctx, StringArgumentType.getString(ctx, "player")))))))
                .then(Commands.literal("bookshelf")
                        .requires(src -> src.getSender().hasPermission(PERM_ADMIN))
                        .executes(this::bookshelf))
                .then(Commands.literal("audit")
                        .requires(src -> src.getSender().hasPermission(PERM_ADMIN))
                        .executes(ctx -> audit(ctx, 15, null))
                        .then(Commands.argument("lines", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> audit(ctx, IntegerArgumentType.getInteger(ctx, "lines"), null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(this::suggestOnline)
                                        .executes(ctx -> audit(ctx, IntegerArgumentType.getInteger(ctx, "lines"),
                                                StringArgumentType.getString(ctx, "player"))))))
                .build();
    }

    // ── Subcommand handlers ─────────────────────────────────────────────────

    private int info(@NotNull CommandContext<CommandSourceStack> ctx) {
        msg().send(ctx, "command.info", Map.of("{version}", plugin.getPluginMeta().getVersion()));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(@NotNull CommandContext<CommandSourceStack> ctx) {
        plugin.reloadPlugin();
        ctx.getSource().getSender().sendMessage(plugin.getMessages().parsed("general.reload-success"));
        return Command.SINGLE_SUCCESS;
    }

    private int give(@NotNull CommandContext<CommandSourceStack> ctx, @Nullable String playerName) {
        final CommandSender sender = ctx.getSource().getSender();
        final String id = StringArgumentType.getString(ctx, "item");

        final Player target = resolveTarget(ctx, playerName);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }

        if (!plugin.getMythicMobsHook().isEnabled()) {
            msg().send(ctx, "command.no-mythicmobs", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        final ItemStack item = plugin.getMythicMobsHook().createItem(id);
        if (item == null) {
            msg().send(ctx, "command.give-unknown", Map.of("{id}", id));
            return Command.SINGLE_SUCCESS;
        }

        giveOrDrop(target, item);
        if (target.equals(sender)) {
            msg().send(ctx, "command.give-self", Map.of("{id}", id));
        } else {
            msg().send(ctx, "command.give-other",
                    Map.of("{id}", id, "{player}", target.getName()));
            target.sendMessage(plugin.getMessages().parsed("command.give-received", Map.of("{id}", id)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int book(@NotNull CommandContext<CommandSourceStack> ctx, @Nullable String playerName) {
        final CommandSender sender = ctx.getSource().getSender();
        final String input = StringArgumentType.getString(ctx, "enchant");
        final int level = IntegerArgumentType.getInteger(ctx, "level");

        final Player target = resolveTarget(ctx, playerName);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }

        final Enchantment ench = resolveEnchant(input);
        if (ench == null) {
            msg().send(ctx, "command.book-unknown", Map.of("{input}", input));
            return Command.SINGLE_SUCCESS;
        }

        final ItemStack book = EnchantmentHelper.applyEnchantment(
                new ItemStack(Material.ENCHANTED_BOOK), ench, level);
        giveOrDrop(target, book);

        final String name = plugin.getEcoHook().displayNameOrFallback(ench);
        if (target.equals(sender)) {
            msg().send(ctx, "command.book-self",
                    Map.of("{enchant}", name, "{level}", String.valueOf(level)));
        } else {
            msg().send(ctx, "command.book-other",
                    Map.of("{enchant}", name, "{level}", String.valueOf(level), "{player}", target.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int bookshelf(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            msg().send(ctx, "command.player-only", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        final var manager = plugin.getEnchantedBookshelfManager();
        final var marks = manager.read(player.getChunk());
        if (marks.isEmpty()) {
            msg().send(ctx, "command.bookshelf-none", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        msg().send(ctx, "command.bookshelf-header",
                Map.of("{count}", String.valueOf(marks.size())));
        marks.forEach((pos, id) -> {
            final Integer power = plugin.getPluginConfig().getEnchantedBookshelfPower(id);
            ctx.getSource().getSender().sendMessage(plugin.getMessages().parsed("command.bookshelf-entry",
                    Map.of("{pos}", pos, "{id}", id, "{power}", power == null ? "?" : String.valueOf(power))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int audit(@NotNull CommandContext<CommandSourceStack> ctx, int lines, @Nullable String playerFilter) {
        final List<String> tail = plugin.getAuditLog().tail(lines, playerFilter);
        if (tail.isEmpty()) {
            msg().send(ctx, "command.audit-empty", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        msg().send(ctx, "command.audit-header", Map.of("{count}", String.valueOf(tail.size())));
        for (String line : tail) {
            // Audit lines are plain text; send them raw (no MiniMessage parsing) to
            // avoid any stray '<' being interpreted as a tag.
            ctx.getSource().getSender().sendMessage(net.kyori.adventure.text.Component.text(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Resolves the target player: the named one (must be online) or the command
     * sender when {@code playerName} is {@code null}. Sends an error and returns
     * {@code null} when resolution fails.
     */
    @Nullable
    private Player resolveTarget(@NotNull CommandContext<CommandSourceStack> ctx, @Nullable String playerName) {
        if (playerName != null) {
            final Player named = Bukkit.getPlayerExact(playerName);
            if (named == null) {
                msg().send(ctx, "command.player-not-found", Map.of("{player}", playerName));
            }
            return named;
        }
        if (ctx.getSource().getSender() instanceof Player self) {
            return self;
        }
        msg().send(ctx, "command.player-only", Map.of());
        return null;
    }

    /** Adds an item to a player's inventory, dropping any overflow at their feet. */
    private static void giveOrDrop(@NotNull Player player, @NotNull ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /**
     * Resolves an enchantment from user input. Tries the full namespaced key first,
     * then falls back to a path-only match (preferring the {@code minecraft}
     * namespace), so admins can type {@code sharpness} instead of the full key.
     */
    @Nullable
    private static Enchantment resolveEnchant(@NotNull String input) {
        final var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        final NamespacedKey direct = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
        if (direct != null) {
            final Enchantment exact = registry.get(direct);
            if (exact != null) {
                return exact;
            }
        }
        Enchantment fallback = null;
        for (Enchantment ench : registry) {
            if (ench.getKey().getKey().equalsIgnoreCase(input)) {
                if (ench.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) {
                    return ench;
                }
                if (fallback == null) {
                    fallback = ench;
                }
            }
        }
        return fallback;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnline(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        final String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(rem)) {
                b.suggest(online.getName());
            }
        }
        return b.buildFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEnchants(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        final String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        final var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        final TreeSet<String> paths = new TreeSet<>();
        for (Enchantment ench : registry) {
            paths.add(ench.getKey().getKey());
        }
        paths.stream().filter(p -> p.startsWith(rem)).forEach(b::suggest);
        return b.buildFuture();
    }

    @NotNull
    private CommandMessenger msg() {
        return (ctx, key, placeholders) ->
                ctx.getSource().getSender().sendMessage(plugin.getMessages().parsed(key, placeholders));
    }

    /** Tiny functional shim so handlers can fire a localized message in one line. */
    @FunctionalInterface
    private interface CommandMessenger {
        void send(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull String key,
                  @NotNull Map<String, String> placeholders);
    }
}
