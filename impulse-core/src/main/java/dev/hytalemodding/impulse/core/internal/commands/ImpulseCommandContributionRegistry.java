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
 * Central contribution point for the sealed {@code /impulse} command tree.
 */
public final class ImpulseCommandContributionRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final Map<String, Supplier<? extends AbstractCommand>> ROOT_CONTRIBUTIONS =
        new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends AbstractCommand>> SETTINGS_CONTRIBUTIONS =
        new LinkedHashMap<>();

    @Nullable
    private static CommandRegistry commandRegistry;
    @Nullable
    private static CommandRegistration commandRegistration;

    private ImpulseCommandContributionRegistry() {
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

    public static synchronized void addRootSubCommand(@Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (ROOT_CONTRIBUTIONS.containsKey(id)) {
            return;
        }
        ROOT_CONTRIBUTIONS.put(id, Objects.requireNonNull(supplier, "supplier"));
        rebuildIfRegistered();
    }

    public static synchronized void addRootAndSettingsSubCommands(
        @Nonnull String rootId,
        @Nonnull Supplier<? extends AbstractCommand> rootSupplier,
        @Nonnull String settingsId,
        @Nonnull Supplier<? extends AbstractCommand> settingsSupplier) {
        boolean changed = addContribution(ROOT_CONTRIBUTIONS, rootId, rootSupplier);
        changed |= addContribution(SETTINGS_CONTRIBUTIONS, settingsId, settingsSupplier);
        if (changed) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void removeRootSubCommand(@Nonnull String id) {
        if (ROOT_CONTRIBUTIONS.remove(id) != null) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void addSettingsSubCommand(@Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (SETTINGS_CONTRIBUTIONS.containsKey(id)) {
            return;
        }
        SETTINGS_CONTRIBUTIONS.put(id, Objects.requireNonNull(supplier, "supplier"));
        rebuildIfRegistered();
    }

    public static synchronized void removeSettingsSubCommand(@Nonnull String id) {
        if (SETTINGS_CONTRIBUTIONS.remove(id) != null) {
            rebuildIfRegistered();
        }
    }

    public static synchronized void removeRootAndSettingsSubCommands(@Nonnull String rootId,
        @Nonnull String settingsId) {
        boolean changed = ROOT_CONTRIBUTIONS.remove(rootId) != null;
        changed |= SETTINGS_CONTRIBUTIONS.remove(settingsId) != null;
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
        ROOT_CONTRIBUTIONS.clear();
        SETTINGS_CONTRIBUTIONS.clear();
    }

    private static void rebuildIfRegistered() {
        if (commandRegistry != null) {
            rebuildRegisteredRoot();
        }
    }

    private static boolean addContribution(
        @Nonnull Map<String, Supplier<? extends AbstractCommand>> contributions,
        @Nonnull String id,
        @Nonnull Supplier<? extends AbstractCommand> supplier) {
        if (contributions.containsKey(id)) {
            return false;
        }
        contributions.put(id, Objects.requireNonNull(supplier, "supplier"));
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
        List<AbstractCommand> settingsContributions = new ArrayList<>(SETTINGS_CONTRIBUTIONS.size());
        for (Supplier<? extends AbstractCommand> supplier : SETTINGS_CONTRIBUTIONS.values()) {
            settingsContributions.add(supplier.get());
        }
        ImpulseCommand command = new ImpulseCommand(settingsContributions);
        for (Supplier<? extends AbstractCommand> supplier : ROOT_CONTRIBUTIONS.values()) {
            command.addRootContribution(supplier.get());
        }
        return command;
    }
}
