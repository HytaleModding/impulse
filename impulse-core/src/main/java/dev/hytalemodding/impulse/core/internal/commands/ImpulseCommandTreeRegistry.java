package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central registry for the sealed {@code /impulse} command tree.
 */
public final class ImpulseCommandTreeRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final Map<String, Supplier<? extends AbstractCommand>> ROOT_COMMANDS =
        new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends AbstractCommand>> DEBUG_COMMANDS =
        new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends AbstractCommand>> SETTINGS_COMMANDS =
        new LinkedHashMap<>();

    @Nullable
    private static CommandRegistry commandRegistry;
    @Nullable
    private static CommandRegistration commandRegistration;

    private ImpulseCommandTreeRegistry() {
    }

    public static synchronized void register(@Nonnull CommandRegistry registry) {
        commandRegistry = Objects.requireNonNull(registry, "registry");
        rebuildRegisteredRoot();
    }

    public static synchronized void unregister() {
        if (commandRegistration != null) {
            commandRegistration.unregister();
            commandRegistration = null;
        }
        commandRegistry = null;
    }

    public static synchronized void registerRootSubCommand(@Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (ROOT_COMMANDS.containsKey(id)) {
            return;
        }
        ROOT_COMMANDS.put(id, Objects.requireNonNull(supplier, "supplier"));
        rebuildIfRegistered();
    }

    public static synchronized void registerRootAndSettingsSubCommands(
        @Nonnull String rootId,
        @Nonnull Supplier<? extends AbstractCommand> rootSupplier,
        @Nonnull String settingsId,
        @Nonnull Supplier<? extends AbstractCommand> settingsSupplier) {
        boolean changed = addCommand(ROOT_COMMANDS, rootId, rootSupplier);
        changed |= addCommand(SETTINGS_COMMANDS, settingsId, settingsSupplier);
        if (changed) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void unregisterRootSubCommand(@Nonnull String id) {
        if (ROOT_COMMANDS.remove(id) != null) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void registerDebugSubCommand(@Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (DEBUG_COMMANDS.containsKey(id)) {
            return;
        }
        DEBUG_COMMANDS.put(id, Objects.requireNonNull(supplier, "supplier"));
        rebuildIfRegistered();
    }

    public static synchronized void unregisterDebugSubCommand(@Nonnull String id) {
        if (DEBUG_COMMANDS.remove(id) != null) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void registerSettingsSubCommand(@Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (SETTINGS_COMMANDS.containsKey(id)) {
            return;
        }
        SETTINGS_COMMANDS.put(id, Objects.requireNonNull(supplier, "supplier"));
        rebuildIfRegistered();
    }

    public static synchronized void unregisterSettingsSubCommand(@Nonnull String id) {
        if (SETTINGS_COMMANDS.remove(id) != null) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void unregisterRootAndSettingsSubCommands(@Nonnull String rootId,
        @Nonnull String settingsId) {
        boolean changed = ROOT_COMMANDS.remove(rootId) != null;
        changed |= SETTINGS_COMMANDS.remove(settingsId) != null;
        if (changed) {
            rebuildIfRegistered();
        }
    }

    @Nonnull
    static synchronized ImpulseCommand createRootCommandForTests() {
        return createRootCommand();
    }

    static synchronized void resetForTests() {
        if (commandRegistration != null) {
            commandRegistration.unregister();
        }
        commandRegistration = null;
        commandRegistry = null;
        ROOT_COMMANDS.clear();
        DEBUG_COMMANDS.clear();
        SETTINGS_COMMANDS.clear();
    }

    private static void rebuildIfRegistered() {
        if (commandRegistry != null) {
            rebuildRegisteredRoot();
        }
    }

    private static boolean addCommand(
        @Nonnull Map<String, Supplier<? extends AbstractCommand>> commands,
        @Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (commands.containsKey(id)) {
            return false;
        }
        commands.put(id, Objects.requireNonNull(supplier, "supplier"));
        return true;
    }

    private static void rebuildRegisteredRoot() {
        CommandRegistry registry = commandRegistry;
        if (registry == null) {
            return;
        }
        if (commandRegistration != null) {
            commandRegistration.unregister();
            commandRegistration = null;
        }
        CommandRegistration registration = registry.registerCommand(createRootCommand());
        if (registration == null) {
            LOGGER.at(Level.SEVERE).log("Failed to register /impulse command root");
            return;
        }
        commandRegistration = registration;
    }

    @Nonnull
    private static ImpulseCommand createRootCommand() {
        List<AbstractCommand> debugCommands = new ArrayList<>(DEBUG_COMMANDS.size());
        for (Supplier<? extends AbstractCommand> supplier : DEBUG_COMMANDS.values()) {
            debugCommands.add(supplier.get());
        }
        List<AbstractCommand> settingsCommands = new ArrayList<>(SETTINGS_COMMANDS.size());
        for (Supplier<? extends AbstractCommand> supplier : SETTINGS_COMMANDS.values()) {
            settingsCommands.add(supplier.get());
        }
        ImpulseCommand command = new ImpulseCommand(debugCommands, settingsCommands);
        for (Supplier<? extends AbstractCommand> supplier : ROOT_COMMANDS.values()) {
            command.registerRootCommand(supplier.get());
        }
        return command;
    }
}
