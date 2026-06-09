/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter;

import dev.scrulius.superenchanter.config.MessagesConfig;
import dev.scrulius.superenchanter.config.PluginConfig;
import dev.scrulius.superenchanter.economy.CostService;
import dev.scrulius.superenchanter.gui.AbstractCustomGUI;
import dev.scrulius.superenchanter.integration.EcoEnchantsHook;
import dev.scrulius.superenchanter.integration.MythicMobsHook;
import dev.scrulius.superenchanter.integration.PlayerPointsHook;
import dev.scrulius.superenchanter.integration.VaultHook;
import dev.scrulius.superenchanter.listener.BlockInteractListener;
import dev.scrulius.superenchanter.listener.BookshelfTrackingListener;
import dev.scrulius.superenchanter.listener.GUIProtectionListener;
import dev.scrulius.superenchanter.listener.VanillaBlockListener;
import dev.scrulius.superenchanter.util.AuditLog;
import dev.scrulius.superenchanter.util.CooldownManager;
import dev.scrulius.superenchanter.util.EnchantedBookshelfManager;
import dev.scrulius.superenchanter.util.LibraryAmbientTask;
import dev.scrulius.superenchanter.util.PendingItemStore;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main plugin class for SuperEnchanter.
 * <p>
 * Replaces vanilla Anvil and Enchanting Table interactions
 * with custom GUIs inspired by Hypixel SkyBlock, featuring
 * multi-economy support and comprehensive anti-dupe protection.
 * </p>
 *
 * @author Scrulius (GitHub)
 */
// Not 'final': MockBukkit proxies the plugin class (ByteBuddy subclass) in tests.
public class SuperEnchanterPlugin extends JavaPlugin {

    private static SuperEnchanterPlugin instance;

    private PluginConfig pluginConfig;
    private MessagesConfig messagesConfig;
    private CooldownManager cooldownManager;
    private PendingItemStore pendingItemStore;
    private EnchantedBookshelfManager enchantedBookshelfManager;
    private EcoEnchantsHook ecoHook;
    private VaultHook vaultHook;
    private PlayerPointsHook playerPointsHook;
    private MythicMobsHook mythicMobsHook;
    private CostService costService;
    private dev.scrulius.superenchanter.integration.MagiaService magiaService; // null if SuperCore absent
    private AuditLog auditLog;
    private LibraryAmbientTask libraryAmbientTask;

    @Override
    public void onEnable() {
        instance = this;
        try {

        // ── Configuration (needed before the fail-safe decision) ──
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);

        // ── Fail-safe: EcoEnchants is mandatory. Skipped under MockBukkit, where no
        //    real plugins are loaded (so unit tests don't trip the shutdown). ──
        if (!isMockEnvironment() && !isEcoEnchantsPresent()) {
            criticalFailure("EcoEnchants no está instalado y es una dependencia OBLIGATORIA.");
            return;
        }

        messagesConfig = new MessagesConfig(this);

        // ── Cooldown Manager ──
        cooldownManager = new CooldownManager(pluginConfig.getCooldownTicks());

        // ── Crash persistence ──
        if (pluginConfig.isCrashPersistenceEnabled()) {
            pendingItemStore = new PendingItemStore(this);
            recoverPendingForOnlinePlayers();
        }

        // ── Enchanted bookshelves (per-block tracking) ──
        enchantedBookshelfManager = new EnchantedBookshelfManager(this);

        // ── Integration Hooks ──
        ecoHook = new EcoEnchantsHook();
        vaultHook = new VaultHook();
        playerPointsHook = new PlayerPointsHook();
        mythicMobsHook = new MythicMobsHook();

        // ── Shared cost service (XP / Vault / PlayerPoints for both GUIs) ──
        costService = new CostService(this);

        // ── Audit log (operation trail for staff forensics) ──
        auditLog = new AuditLog(this);

        // ── Magia (skill loop) — only if SuperCore is present (it bridges to EcoSkills) ──
        if (getServer().getPluginManager().isPluginEnabled("SuperCore")) {
            magiaService = new dev.scrulius.superenchanter.integration.MagiaService(this);
        }

        // ── PlaceholderAPI — expose %superenchanter_magia_*% (guarded so PAPI classes
        //    are only linked when the plugin is actually installed). ──
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new dev.scrulius.superenchanter.integration.SuperEnchanterPlaceholders(this).register();
            getLogger().info("✔ Registered PlaceholderAPI expansion 'superenchanter'.");
        }

        getLogger().info("✔ Hooked into EcoEnchants (required dependency).");
        logHookStatus("Vault", vaultHook.isEnabled());
        logHookStatus("PlayerPoints", playerPointsHook.isEnabled());
        logHookStatus("MythicMobs", mythicMobsHook.isEnabled());
        if (magiaService != null) {
            logHookStatus("SuperCore (Magia)", magiaService.isEnabled());
        } else {
            getLogger().info("SuperCore ausente — la skill Magia está desactivada.");
        }

        // Diagnose the EcoEnchants integration so silent breakage (e.g. requirement
        // lines not loading) is loud in the console. Skipped under MockBukkit.
        if (!isMockEnvironment()) {
            ecoHook.logIntegrationSelfCheck(getLogger());
        }

        // ── Register Listeners ──
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GUIProtectionListener(this), this);
        pm.registerEvents(new BlockInteractListener(this), this);
        pm.registerEvents(new VanillaBlockListener(this), this);
        pm.registerEvents(new BookshelfTrackingListener(this), this);
        pm.registerEvents(new dev.scrulius.superenchanter.listener.LootControlListener(this), this);
        pm.registerEvents(new dev.scrulius.superenchanter.listener.BannedEnchantmentListener(this), this);
        pm.registerEvents(new dev.scrulius.superenchanter.listener.VillagerTradeListener(this), this);

        // ── Commands ──
        registerCommands();

        // ── Ambient particles over enchanted libraries ──
        startLibraryAmbient();

        getLogger().info("SuperEnchanter v" + getPluginMeta().getVersion() + " enabled!");
        getLogger().info("Custom Anvil & Enchanting Table GUIs are active.");

        } catch (Throwable t) {
            getLogger().severe("Excepción crítica durante la inicialización de SuperEnchanter:");
            t.printStackTrace();
            criticalFailure("Excepción durante la inicialización: " + t.getMessage());
        }
    }

    /** @return whether EcoEnchants is installed and enabled. */
    private boolean isEcoEnchantsPresent() {
        var eco = getServer().getPluginManager().getPlugin("EcoEnchants");
        return eco != null && eco.isEnabled();
    }

    /** @return whether we're running under a MockBukkit test server (no real plugins). */
    private boolean isMockEnvironment() {
        return getServer().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("mock");
    }

    /**
     * Handles a critical startup failure: logs a loud banner and, per config
     * {@code fail-safe.shutdown-on-critical-error} (default true), shuts the whole
     * server down instead of letting it run without SuperEnchanter. Never shuts the
     * server down under MockBukkit.
     */
    private void criticalFailure(@NotNull String reason) {
        getLogger().severe("================ SUPERENCHANTER ================");
        getLogger().severe(" NO se pudo arrancar: " + reason);
        boolean shutdown = (pluginConfig == null || pluginConfig.isShutdownOnCriticalError())
                && !isMockEnvironment();
        if (shutdown) {
            getLogger().severe(" fail-safe ACTIVO -> apagando el servidor.");
            getLogger().severe("================================================");
            getServer().shutdown();
        } else {
            getLogger().severe(" Desactivando solo el plugin (el server sigue).");
            getLogger().severe("================================================");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Close all active GUIs and return items to players
        AbstractCustomGUI.closeAll();

        // Stop ambient particle task
        stopLibraryAmbient();

        // Clear cooldowns
        if (cooldownManager != null) {
            cooldownManager.clearAll();
        }

        getLogger().info("SuperEnchanter disabled. All GUIs closed, items returned.");
        instance = null;
    }

    // ── Accessors ──

    /** @return the singleton plugin instance */
    public static @NotNull SuperEnchanterPlugin getInstance() {
        return instance;
    }

    /** @return the typed configuration wrapper */
    public @NotNull PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    /** @return the messages configuration */
    public @NotNull MessagesConfig getMessages() {
        return messagesConfig;
    }

    /** @return the per-player cooldown manager */
    public @NotNull CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /** @return the crash-persistence store, or {@code null} if the feature is disabled */
    public @Nullable PendingItemStore getPendingItemStore() {
        return pendingItemStore;
    }

    /** @return the enchanted-bookshelf (per-block) tracker */
    public @NotNull EnchantedBookshelfManager getEnchantedBookshelfManager() {
        return enchantedBookshelfManager;
    }

    /** @return the MythicMobs soft-hook */
    public @NotNull MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    /** @return the Magia skill loop service, or {@code null} if SuperCore is not installed. */
    public @org.jetbrains.annotations.Nullable dev.scrulius.superenchanter.integration.MagiaService getMagiaService() {
        return magiaService;
    }

    /** @return the EcoEnchants soft-hook */
    public @NotNull EcoEnchantsHook getEcoHook() {
        return ecoHook;
    }

    /** @return the Vault economy soft-hook */
    public @NotNull VaultHook getVaultHook() {
        return vaultHook;
    }

    /** @return the PlayerPoints soft-hook */
    public @NotNull PlayerPointsHook getPlayerPointsHook() {
        return playerPointsHook;
    }

    /** @return the shared multi-currency cost service used by both GUIs */
    public @NotNull CostService getCostService() {
        return costService;
    }

    /** @return the operation audit log */
    public @NotNull AuditLog getAuditLog() {
        return auditLog;
    }

    // ── Commands ──

    /**
     * Registers the {@code /superenchanter} command (with a {@code reload}
     * subcommand) via Paper's modern Brigadier lifecycle API.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    new dev.scrulius.superenchanter.command.SuperEnchanterCommand(this).build(),
                    "SuperEnchanter admin commands",
                    List.of("se")
            );
        });
    }

    /**
     * Reloads configuration and messages from disk and refreshes the runtime
     * state derived from them (cooldown duration, crash-persistence store).
     */
    public void reloadPlugin() {
        pluginConfig.reload();
        messagesConfig.reload();
        // Drop cached EcoEnchants data so edited ymls (names/descriptions/requirement
        // lines) reflect after /ecoenchants reload without a full restart.
        if (ecoHook != null) {
            ecoHook.clearCaches();
        }
        if (magiaService != null) {
            magiaService.reload();
        }
        cooldownManager = new CooldownManager(pluginConfig.getCooldownTicks());
        if (pluginConfig.isCrashPersistenceEnabled()) {
            if (pendingItemStore == null) {
                pendingItemStore = new PendingItemStore(this);
            }
        } else {
            pendingItemStore = null;
        }
        // Re-apply the library-particles toggle from the reloaded config.
        startLibraryAmbient();
    }

    // ── Library ambient particles ──

    /** (Re)starts the ambient particle task, honouring the configured toggle and period. */
    private void startLibraryAmbient() {
        stopLibraryAmbient();
        if (pluginConfig.getLibraryAmbientParticle().enabled()) {
            long period = pluginConfig.getLibraryAmbientPeriodTicks();
            libraryAmbientTask = new LibraryAmbientTask(this);
            libraryAmbientTask.runTaskTimer(this, period, period);
        }
    }

    /** Cancels the ambient particle task if it is running. */
    private void stopLibraryAmbient() {
        if (libraryAmbientTask != null) {
            try {
                libraryAmbientTask.cancel();
            } catch (IllegalStateException ignored) {
                // not scheduled — nothing to cancel
            }
            libraryAmbientTask = null;
        }
    }

    // ── Crash persistence ──

    /**
     * Returns any crash-persisted GUI items to the player and clears their entry.
     * Safe to call unconditionally — does nothing when the feature is disabled or
     * the player has no pending items.
     *
     * @param player the player to restore items to
     */
    public void restorePendingItems(@NotNull Player player) {
        if (pendingItemStore == null) {
            return;
        }
        List<ItemStack> items = pendingItemStore.drain(player.getUniqueId());
        if (items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.sendMessage(messagesConfig.parsed("general.items-recovered"));
    }

    /** Restores pending items for players already online (e.g. after a {@code /reload}). */
    private void recoverPendingForOnlinePlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            restorePendingItems(player);
        }
    }

    // ── Helpers ──

    private void logHookStatus(String hookName, boolean enabled) {
        if (enabled) {
            getLogger().info("✔ " + hookName + " detected and hooked.");
        } else {
            getLogger().info("✘ " + hookName + " not found (optional).");
        }
    }
}
