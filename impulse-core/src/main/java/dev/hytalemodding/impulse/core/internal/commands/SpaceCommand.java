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
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
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
        addSubCommand(new DefaultCommand());
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
        private final OptionalArg<String> defaultArg = withOptionalArg(
            "default",
            "Whether this space becomes default: true or false",
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

            Boolean makeDefault = defaultArg.provided(context)
                ? parseBoolean(defaultArg.get(context))
                : Boolean.TRUE;
            if (makeDefault == null) {
                context.sendMessage(Message.raw("default must be true or false."));
                return;
            }

            PhysicsSpaceSettings settings = worldCollisionMode == WorldCollisionMode.STREAMING
                ? PhysicsSpaceSettings.streamingWorldCollision()
                : PhysicsSpaceSettings.defaults();
            settings.setWorldCollisionMode(worldCollisionMode);

            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            try {
                PhysicsSpace space = resource.createSpace(backendId,
                    world.getName(),
                    settings,
                    makeDefault);
                context.sendMessage(Message.raw("Created physics space id="
                    + space.getId().value()
                    + " backend=" + space.getBackendId().value()
                    + " worldCollision=" + worldCollisionMode.name().toLowerCase(Locale.ROOT)
                    + (makeDefault ? " default=true" : " default=false")));
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
            List<PhysicsSpace> spaces = new ArrayList<>(resource.getSpaces());
            spaces.sort(Comparator.comparingInt(space -> space.getId().value()));

            context.sendMessage(Message.raw("Physics spaces in world " + world.getName() + ":"));
            if (spaces.isEmpty()) {
                context.sendMessage(Message.raw("- <none>"));
                return;
            }

            SpaceId defaultSpaceId = resource.getDefaultSpaceId();
            for (PhysicsSpace space : spaces) {
                PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
                SpaceCounts counts = countSpaceContents(store, space);
                String marker = space.getId().equals(defaultSpaceId) ? " default=true" : "";
                context.sendMessage(Message.raw("- id=" + space.getId().value()
                    + " backend=" + space.getBackendId().value()
                    + " bodies=" + counts.bodies()
                    + " joints=" + counts.joints()
                    + " worldCollision="
                    + settings.getWorldCollisionMode().name().toLowerCase(Locale.ROOT)
                    + marker));
            }
        }
    }

    private static final class DefaultCommand extends AbstractWorldCommand {

        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Space id to make default",
            ArgTypes.INTEGER);

        private DefaultCommand() {
            super("default", "Show or set the default physics space", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            if (!spaceArg.provided(context)) {
                SpaceId defaultSpaceId = resource.getDefaultSpaceId();
                context.sendMessage(Message.raw(defaultSpaceId == null
                    ? "No default physics space is configured."
                    : "Default physics space id=" + defaultSpaceId.value()));
                return;
            }

            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return;
            }

            SpaceId spaceId = new SpaceId(rawSpaceId);
            try {
                resource.setDefaultSpaceId(spaceId);
                context.sendMessage(Message.raw("Default physics space set to id="
                    + spaceId.value() + " in world " + world.getName() + "."));
            } catch (RuntimeException exception) {
                context.sendMessage(Message.raw("Failed to set default physics space: "
                    + exception.getMessage()));
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
            PhysicsSpace space = resource.getSpace(spaceId);
            if (space == null) {
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
            SpaceCounts counts = countSpaceContents(store, space);
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
    private static SpaceCounts countSpaceContents(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space) {
        return PhysicsWorkerAccess.call(store, "count physics space contents",
            () -> new SpaceCounts(space.bodyCount(), space.getJoints().size()));
    }

    private static int countRegisteredBodies(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId) {
        int count = 0;
        for (PhysicsWorldResource.BodyRegistration registration : resource.getBodyRegistrations()) {
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
            return useDefaultWhenMissing ? ImpulsePlugin.get().getDefaultBackendId() : null;
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

    @Nullable
    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1", "default" -> Boolean.TRUE;
            case "false", "no", "n", "0", "nodefault" -> Boolean.FALSE;
            default -> null;
        };
    }

    private record SpaceCounts(int bodies, int joints) {
    }
}
