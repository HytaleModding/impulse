package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpaceCommand extends AbstractCommandCollection {

    public SpaceCommand() {
        super("space", "Explicit physics space lifecycle commands");
        addSubCommand(new CreateCommand());
        addSubCommand(new ListCommand());
        addSubCommand(new DeleteCommand());
    }

    private static final class CreateCommand extends AbstractWorldCommand {

        private final OptionalArg<String> backendArg = withOptionalArg(
            "backend",
            "Backend id, for example impulse:rapier",
            ArgTypes.STRING);
        private final OptionalArg<String> worldCollisionArg = withOptionalArg(
            "worldCollision",
            "World collision mode: none, manual, or streaming",
            ArgTypes.STRING);
        private CreateCommand() {
            super("create", "Create an explicit physics space", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            BackendId backendId = parseBackendId(context, backendArg, true);
            if (backendId == null) {
                return;
            }

            WorldCollisionMode worldCollisionMode = worldCollisionArg.provided(context)
                ? parseWorldCollisionMode(worldCollisionArg.get(context))
                : WorldCollisionMode.STREAMING;
            if (worldCollisionMode == null) {
                context.sendMessage(Message.raw("worldCollision must be none, manual, or streaming."));
                return;
            }

            PhysicsSpaceSettings settings = worldCollisionMode == WorldCollisionMode.STREAMING
                ? PhysicsSpaceSettings.streamingWorldCollision()
                : PhysicsSpaceSettings.defaults();
            settings.getWorldCollisionSettings().setWorldCollisionMode(worldCollisionMode);

            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            try {
                SpaceId spaceId = resource.createSpace(backendId,
                    world.getName(),
                    settings);
                context.sendMessage(Message.raw("Created physics space id="
                    + spaceId.value()
                    + " backend=" + backendId.value()
                    + " worldCollision=" + worldCollisionMode.name().toLowerCase(Locale.ROOT)
                    + "."));
            } catch (RuntimeException exception) {
                context.sendMessage(Message.raw("Failed to create physics space: "
                    + exception.getMessage()));
            }
        }
    }

    private static final class ListCommand extends AbstractWorldCommand {

        private ListCommand() {
            super("list", "List physics spaces in the target world", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            List<SpaceListEntry> spaces = resource.callOnPhysicsOwner("list physics spaces", access -> {
                List<SpaceListEntry> entries = new ArrayList<>();
                for (PhysicsSpace space : access.getSpaces()) {
                    PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
                    SpaceCounts counts = countSpaceContents(space);
                    entries.add(new SpaceListEntry(space.getId(),
                        space.getBackendId().value(),
                        counts.bodies(),
                        counts.joints(),
                        settings.getWorldCollisionSettings().getWorldCollisionMode()));
                }
                entries.sort(Comparator.comparingInt(entry -> entry.spaceId().value()));
                return entries;
            });

            context.sendMessage(Message.raw("Physics spaces in world " + world.getName() + ":"));
            if (spaces.isEmpty()) {
                context.sendMessage(Message.raw("- <none>"));
                return;
            }

            for (SpaceListEntry space : spaces) {
                context.sendMessage(Message.raw("- id=" + space.spaceId().value()
                    + " backend=" + space.backendId()
                    + " bodies=" + space.bodies()
                    + " joints=" + space.joints()
                    + " worldCollision="
                    + space.worldCollisionMode().name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    private static final class DeleteCommand extends AbstractWorldCommand {

        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Space id to delete",
            ArgTypes.INTEGER);

        private DeleteCommand() {
            super("delete", "Delete a physics space and its runtime backend state", true);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            if (!spaceArg.provided(context)) {
                context.sendMessage(Message.raw("Missing space id. Example:"
                    + " /impulse space delete --space=1 --confirm"));
                return;
            }

            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return;
            }

            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            SpaceId spaceId = new SpaceId(rawSpaceId);
            if (!resource.hasSpace(spaceId)) {
                context.sendMessage(Message.raw("No physics space id=" + rawSpaceId
                    + " exists in world " + world.getName() + "."));
                return;
            }

            /*
             * Backend-only bodies can be generated by systems such as streaming world collision.
             * Those bodies belong to the space/cache lifecycle and are removed when the space is
             * deleted. Registered bodies are gameplay/runtime resources addressed by PhysicsBodyId,
             * so they still require an explicit clean/destroy before deleting the space.
             */
            int registeredBodies = countRegisteredBodies(resource, spaceId);
            SpaceCounts counts = resource.callOnPhysicsOwner("count physics space contents",
                access -> countSpaceContents(access.requireSpace(spaceId)));
            int backendBodies = counts.bodies();
            int joints = counts.joints();
            if (registeredBodies > 0 || joints > 0) {
                context.sendMessage(Message.raw("Physics space id=" + rawSpaceId
                    + " is not empty (" + registeredBodies + " registered bodies, "
                    + backendBodies + " backend bodies, " + joints + " joints)."
                    + " Use /impulse clean for populated worlds, then delete empty spaces."));
                return;
            }

            resource.removeSpace(spaceId, world.getName());
            context.sendMessage(Message.raw("Deleted physics space id=" + rawSpaceId
                + " with " + backendBodies + " backend bodies and " + joints + " joints."));
        }
    }

    @Nonnull
    private static SpaceCounts countSpaceContents(@Nonnull PhysicsSpace space) {
        return new SpaceCounts(space.bodyCount(), space.getJoints().size());
    }

    private static int countRegisteredBodies(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId) {
        int count = 0;
        for (PhysicsBodyRegistrationView registration : resource.getBodyRegistrationViews()) {
            if (registration.spaceId().equals(spaceId)) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static BackendId parseBackendId(@Nonnull CommandContext context,
        @Nonnull OptionalArg<String> backendArg,
        boolean useDefaultWhenMissing) {
        if (!backendArg.provided(context)) {
            if (!useDefaultWhenMissing) {
                return null;
            }

            BackendId defaultBackendId = ImpulsePlugin.get().getDefaultBackendId();
            if (defaultBackendId != null) {
                return defaultBackendId;
            }

            context.sendMessage(Message.raw("Missing backend id. Multiple backends are installed; "
                + "use --backend=<id>. Available backends: " + availableBackendIds()));
            return null;
        }

        String rawBackendId = backendArg.get(context).trim();
        try {
            return new BackendId(rawBackendId);
        } catch (RuntimeException exception) {
            context.sendMessage(Message.raw("Invalid backend id: " + rawBackendId));
            return null;
        }
    }

    @Nullable
    private static WorldCollisionMode parseWorldCollisionMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "off", "disabled" -> WorldCollisionMode.NONE;
            case "manual" -> WorldCollisionMode.MANUAL;
            case "streaming", "stream", "on", "enabled" -> WorldCollisionMode.STREAMING;
            default -> null;
        };
    }

    @Nonnull
    private static String availableBackendIds() {
        List<String> backendIds = new ArrayList<>();
        for (PhysicsBackend backend : Impulse.getBackends()) {
            backendIds.add(backend.getId().value());
        }
        backendIds.sort(String::compareTo);
        return backendIds.isEmpty() ? "<none>" : String.join(", ", backendIds);
    }

    private record SpaceCounts(int bodies, int joints) {
    }

    private record SpaceListEntry(@Nonnull SpaceId spaceId,
                                  @Nonnull String backendId,
                                  int bodies,
                                  int joints,
                                  @Nonnull WorldCollisionMode worldCollisionMode) {
    }
}
