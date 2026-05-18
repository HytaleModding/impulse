package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.examples.ImpulseExamplesPlugin;
import dev.hytalemodding.impulse.examples.commands.persistence.PersistentPhysicsSnapshotBody;
import dev.hytalemodding.impulse.examples.commands.persistence.PersistentPhysicsSnapshotFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/**
 * Manual save and load commands for Impulse physics state.
 *
 * <p>Unlike the seamless Hytale-native persistence (which is automatic), these
 * commands write and read explicit snapshot files for ad-hoc use: debugging,
 * experimentation, or saving a specific physics arrangement for later.</p>
 *
 * <p>{@code save} captures the current persisted world resource and all entity-backed
 * body components into a BSON/JSON file. {@code load} reads a snapshot, matches
 * bodies to the existing persisted-physics entity set by UUID, updates their
 * persisted state, clears the runtime physics, and lets the hydration systems
 * rebuild onto those same entities without respawning duplicates.</p>
 *
 * <p>The snapshot format is narrower than a generic UUID remapping tool. It covers
 * entity-backed bodies and joints between entity-backed bodies. Generated world
 * collision, runtime helper bodies, and debug/runtime-only state remain outside
 * the snapshot contract.</p>
 */
public class PersistenceCommand extends AbstractCommandCollection {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final Query<EntityStore> PERSISTENT_BODY_QUERY = Query.and(
        PersistentPhysicsBodyComponent.getComponentType(),
        UUIDComponent.getComponentType());

    public PersistenceCommand() {
        super("persistence", "Save and load Impulse example physics state");
        addSubCommand(new SaveCommand());
        addSubCommand(new LoadCommand());
    }

    private abstract static class SnapshotCommand extends AbstractAsyncPlayerCommand {

        protected static final String DEFAULT_NAME = "default";

        protected final OptionalArg<String> nameArg = withOptionalArg(
            "name",
            "Snapshot name",
            ArgTypes.STRING);

        protected SnapshotCommand(@Nonnull String name, @Nonnull String description) {
            super(name, description);
        }

        @Nonnull
        protected String snapshotName(@Nonnull CommandContext ctx) {
            if (!nameArg.provided(ctx)) {
                return DEFAULT_NAME;
            }

            String name = nameArg.get(ctx).trim();
            if (name.isEmpty()) {
                return DEFAULT_NAME;
            }
            if (!name.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException(
                    "Snapshot name may only use letters, numbers, dot, dash, and underscore");
            }
            return name;
        }

        @Nonnull
        protected Path snapshotPath(@Nonnull String name) {
            return ImpulseExamplesPlugin.get()
                .getDataDirectory()
                .resolve("snapshots")
                .resolve(name + ".impulse-state.json");
        }
    }

    private static final class SaveCommand extends SnapshotCommand {

        private SaveCommand() {
            super("save", "Save entity-backed Impulse persistence state");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            String name;
            try {
                name = snapshotName(ctx);
            } catch (IllegalArgumentException exception) {
                ctx.sender().sendMessage(Message.raw(exception.getMessage()));
                return CompletableFuture.completedFuture(null);
            }

            PersistentPhysicsSnapshotFile snapshot = buildSnapshot(store);
            Path path = snapshotPath(name);
            try {
                BsonUtil.writeSync(path, PersistentPhysicsSnapshotFile.CODEC, snapshot, LOGGER);
            } catch (IOException exception) {
                ctx.sender().sendMessage(Message.raw("Failed to save snapshot: "
                    + exception.getMessage()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw("Saved snapshot '" + name + "' to " + path
                + " (" + snapshot.getWorld().getSpaces().length + " spaces, "
                + snapshot.getBodies().length + " bodies, "
                + snapshot.getWorld().getJoints().length + " joints)."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class LoadCommand extends SnapshotCommand {

        private final OptionalArg<String> confirmArg = withOptionalArg(
            "confirm",
            "Required: true, because load resets runtime physics and removes unmatched persisted bodies",
            ArgTypes.STRING);

        private LoadCommand() {
            super("load", "Load Impulse persistence state onto existing Hytale entities");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            String name;
            try {
                name = snapshotName(ctx);
            } catch (IllegalArgumentException exception) {
                ctx.sender().sendMessage(Message.raw(exception.getMessage()));
                return CompletableFuture.completedFuture(null);
            }

            Path path = snapshotPath(name);
            if (!Files.exists(path)) {
                ctx.sender().sendMessage(Message.raw("Snapshot '" + name + "' does not exist."));
                return CompletableFuture.completedFuture(null);
            }

            PersistentPhysicsSnapshotFile snapshot;
            try {
                snapshot = readSnapshot(path);
            } catch (IOException exception) {
                ctx.sender().sendMessage(Message.raw("Failed to load snapshot: "
                    + exception.getMessage()));
                return CompletableFuture.completedFuture(null);
            }

            Map<UUID, Ref<EntityStore>> existingEntities = collectPersistentEntityRefs(store);
            Map<UUID, PersistentPhysicsBodyComponent> snapshotBodies = new HashMap<>();
            int missingEntities = 0;
            for (PersistentPhysicsSnapshotBody entry : snapshot.getBodies()) {
                if (entry.getEntityUuid() == null || entry.getBody() == null) {
                    continue;
                }
                if (!existingEntities.containsKey(entry.getEntityUuid())) {
                    missingEntities++;
                    continue;
                }
                snapshotBodies.put(entry.getEntityUuid(), entry.getBody().clone());
            }

            Set<UUID> keptEntities = snapshotBodies.keySet();
            PersistentPhysicsWorldResource targetWorld = filteredWorld(snapshot.getWorld(), snapshotBodies);
            String validationFailure = validateSnapshotTarget(targetWorld, snapshotBodies);
            if (validationFailure != null) {
                ctx.sender().sendMessage(Message.raw("Snapshot '" + name + "' cannot be loaded: "
                    + validationFailure));
                return CompletableFuture.completedFuture(null);
            }

            if (!confirmDestructiveLoad(ctx, name, targetWorld, keptEntities.size(),
                missingEntities, existingEntities.size() - keptEntities.size())) {
                return CompletableFuture.completedFuture(null);
            }

            PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
            resetRuntimePhysics(store, world, runtime);

            PersistentPhysicsWorldResource persistentWorld = store.getResource(
                PersistentPhysicsWorldResource.getResourceType());
            persistentWorld.copyFrom(targetWorld);
            persistentWorld.markRuntimeRestorePending();

            int removedBodies = 0;
            for (Map.Entry<UUID, Ref<EntityStore>> entry : existingEntities.entrySet()) {
                UUID uuid = entry.getKey();
                Ref<EntityStore> entityRef = entry.getValue();
                if (keptEntities.contains(uuid)) {
                    PersistentPhysicsBodyComponent body = snapshotBodies.get(uuid).clone();
                    body.markForBodyRebuild();
                    store.putComponent(entityRef, PersistentPhysicsBodyComponent.getComponentType(), body);
                    continue;
                }

                if (store.getComponent(entityRef, PersistentPhysicsBodyComponent.getComponentType()) != null) {
                    store.removeComponent(entityRef, PersistentPhysicsBodyComponent.getComponentType());
                    removedBodies++;
                }
            }

            ctx.sender().sendMessage(Message.raw("Loaded snapshot '" + name + "' from " + path
                + " without respawning entities (" + targetWorld.getSpaces().length + " spaces, "
                + keptEntities.size() + " matched bodies, "
                + targetWorld.getJoints().length + " joints, "
                + missingEntities + " missing entities skipped, "
                + removedBodies + " bodies removed)."));
            return CompletableFuture.completedFuture(null);
        }

        private boolean confirmDestructiveLoad(@Nonnull CommandContext ctx,
            @Nonnull String name,
            @Nonnull PersistentPhysicsWorldResource targetWorld,
            int matchedBodies,
            int missingBodies,
            int removedBodies) {
            if (confirmArg.provided(ctx) && "true".equalsIgnoreCase(confirmArg.get(ctx).trim())) {
                return true;
            }

            ctx.sender().sendMessage(Message.raw("Loading snapshot '" + name + "' resets runtime "
                + "physics, replaces persisted spaces and joints, rebuilds matched bodies, and "
                + "removes persistence from current entity-backed bodies not present in the snapshot "
                + "(" + targetWorld.getSpaces().length + " spaces, "
                + matchedBodies + " matched bodies, "
                + targetWorld.getJoints().length + " joints, "
                + missingBodies + " missing entities skipped, "
                + removedBodies + " bodies removed). Re-run with --confirm true to continue."));
            return false;
        }
    }

    @Nonnull
    private static PersistentPhysicsSnapshotFile buildSnapshot(@Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource world = store.getResource(
            PersistentPhysicsWorldResource.getResourceType()).clone();
        List<PersistentPhysicsSnapshotBody> bodies = new ArrayList<>();
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> consumer =
            (chunk, commandBuffer) ->
            collectBodies(chunk, bodies);
        store.forEachChunk(PERSISTENT_BODY_QUERY, consumer);
        return new PersistentPhysicsSnapshotFile(world,
            bodies.toArray(PersistentPhysicsSnapshotBody[]::new));
    }

    private static void collectBodies(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull List<PersistentPhysicsSnapshotBody> bodies) {
        for (int index = 0; index < chunk.size(); index++) {
            PersistentPhysicsBodyComponent body = chunk.getComponent(index,
                PersistentPhysicsBodyComponent.getComponentType());
            UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (body == null || uuid == null) {
                continue;
            }
            bodies.add(new PersistentPhysicsSnapshotBody(uuid.getUuid(), body.clone()));
        }
    }

    @Nonnull
    private static PersistentPhysicsSnapshotFile readSnapshot(@Nonnull Path path) throws IOException {
        BsonDocument document = BsonUtil.readDocumentNow(path);
        if (document == null) {
            throw new IOException("Snapshot file is empty or unreadable");
        }

        ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
        PersistentPhysicsSnapshotFile snapshot = PersistentPhysicsSnapshotFile.CODEC.decode(document,
            extraInfo);
        extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
        return snapshot;
    }

    @Nonnull
    private static Map<UUID, Ref<EntityStore>> collectPersistentEntityRefs(@Nonnull Store<EntityStore> store) {
        Map<UUID, Ref<EntityStore>> refs = new HashMap<>();
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> consumer =
            (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                if (uuid == null) {
                    continue;
                }
                refs.put(uuid.getUuid(), chunk.getReferenceTo(index));
            }
            };
        store.forEachChunk(PERSISTENT_BODY_QUERY, consumer);
        return refs;
    }

    @Nonnull
    private static PersistentPhysicsWorldResource filteredWorld(
        @Nonnull PersistentPhysicsWorldResource source,
        @Nonnull Map<UUID, PersistentPhysicsBodyComponent> availableBodies) {
        PersistentPhysicsWorldResource filtered = source.clone();
        Set<UUID> availableEntityUuids = availableBodies.keySet();
        Set<Integer> retainedSpaceIds = new HashSet<>();
        for (PersistentPhysicsBodyComponent body : availableBodies.values()) {
            int resolvedSpaceId = body.resolveSpaceId(source.getDefaultSpaceIdValue());
            if (resolvedSpaceId > 0) {
                retainedSpaceIds.add(resolvedSpaceId);
            }
        }

        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PersistentPhysicsJointState joint : source.getJoints()) {
            if (joint.getBodyAUuid() == null || joint.getBodyBUuid() == null) {
                continue;
            }
            if (!availableEntityUuids.contains(joint.getBodyAUuid())
                || !availableEntityUuids.contains(joint.getBodyBUuid())) {
                continue;
            }
            PersistentPhysicsBodyComponent bodyA = availableBodies.get(joint.getBodyAUuid());
            PersistentPhysicsBodyComponent bodyB = availableBodies.get(joint.getBodyBUuid());
            int bodyASpaceId = bodyA.resolveSpaceId(source.getDefaultSpaceIdValue());
            int bodyBSpaceId = bodyB.resolveSpaceId(source.getDefaultSpaceIdValue());
            if (joint.getSpaceId() <= 0
                || bodyASpaceId != joint.getSpaceId()
                || bodyBSpaceId != joint.getSpaceId()) {
                continue;
            }
            joints.add(joint.copy());
            retainedSpaceIds.add(joint.getSpaceId());
        }

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        for (PersistentPhysicsSpaceState space : source.getSpaces()) {
            if (retainedSpaceIds.contains(space.getSpaceId())) {
                spaces.add(space.copy());
            }
        }
        if (!retainedSpaceIds.contains(filtered.getDefaultSpaceId())) {
            filtered.setDefaultSpaceId(PersistentPhysicsBodyComponent.DEFAULT_SPACE_ID);
        }
        filtered.setSpaces(spaces.toArray(PersistentPhysicsSpaceState[]::new));
        filtered.setJoints(joints.toArray(PersistentPhysicsJointState[]::new));
        return filtered;
    }

    @Nullable
    private static String validateSnapshotTarget(@Nonnull PersistentPhysicsWorldResource targetWorld,
        @Nonnull Map<UUID, PersistentPhysicsBodyComponent> availableBodies) {
        int steps = targetWorld.getSimulationSteps();
        if (steps < PhysicsWorldResource.MIN_SIMULATION_STEPS
            || steps > PhysicsWorldResource.MAX_SIMULATION_STEPS) {
            return "simulation steps must be between " + PhysicsWorldResource.MIN_SIMULATION_STEPS
                + " and " + PhysicsWorldResource.MAX_SIMULATION_STEPS;
        }
        if (targetWorld.getStepMode() == null) {
            return "step mode is missing";
        }
        if (!Float.isFinite(targetWorld.getMaxStepDt()) || targetWorld.getMaxStepDt() <= 0.0f) {
            return "max step dt must be finite and positive";
        }

        Set<Integer> spaceIds = new HashSet<>();
        for (PersistentPhysicsSpaceState space : targetWorld.getSpaces()) {
            if (space.getSpaceId() <= 0) {
                return "space id must be positive, found " + space.getSpaceId();
            }
            if (!spaceIds.add(space.getSpaceId())) {
                return "duplicate space id " + space.getSpaceId();
            }
            try {
                Impulse.getBackend(space.toBackendId());
            } catch (RuntimeException exception) {
                return "saved backend " + space.getBackendId() + " is not available";
            }
            try {
                space.toSettings();
            } catch (RuntimeException exception) {
                return "space " + space.getSpaceId() + " settings are invalid: "
                    + exception.getMessage();
            }
        }

        int defaultSpaceId = targetWorld.getDefaultSpaceId();
        if (defaultSpaceId > 0 && !spaceIds.contains(defaultSpaceId)) {
            return "default space id " + defaultSpaceId + " is not present in the filtered snapshot";
        }
        for (Map.Entry<UUID, PersistentPhysicsBodyComponent> entry : availableBodies.entrySet()) {
            PersistentPhysicsBodyComponent body = entry.getValue();
            int spaceId = body.resolveSpaceId(targetWorld.getDefaultSpaceIdValue());
            if (spaceId <= 0 || !spaceIds.contains(spaceId)) {
                return "body " + entry.getKey() + " references missing space id " + spaceId;
            }
            if (body.getShapeType() == null
                || body.getShapeType() == ShapeType.UNKNOWN
                || body.getShapeType() == ShapeType.VOXELS) {
                return "body " + entry.getKey() + " has unsupported persistent shape "
                    + body.getShapeType();
            }
        }
        for (PersistentPhysicsJointState joint : targetWorld.getJoints()) {
            if (joint.getSpaceId() <= 0 || !spaceIds.contains(joint.getSpaceId())) {
                return "joint references missing space id " + joint.getSpaceId();
            }
            if (joint.getBodyAUuid() == null || joint.getBodyBUuid() == null) {
                return "joint endpoint UUID is missing";
            }
        }
        return null;
    }

    private static void resetRuntimePhysics(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource runtime) {
        for (Ref<EntityStore> owner : runtime.getBodyOwners()) {
            if (!owner.isValid()) {
                continue;
            }

            PhysicsBodyComponent bodyComponent = store.getComponent(owner,
                PhysicsBodyComponent.getComponentType());
            if (bodyComponent == null) {
                continue;
            }

            var space = bodyComponent.getSpaceId() != null
                ? runtime.getSpace(bodyComponent.getSpaceId())
                : runtime.getDefaultSpace();
            if (space != null) {
                space.removeBody(bodyComponent.getBody());
            }
            runtime.unregisterBodyOwner(bodyComponent.getBody(), owner);
            store.removeComponent(owner, PhysicsBodyComponent.getComponentType());
            if (store.getComponent(owner, ImpulseControllableComponent.getComponentType()) != null) {
                store.removeComponent(owner, ImpulseControllableComponent.getComponentType());
            }
        }
        runtime.clearBodyOwners();
        runtime.clearAllSpaces(world.getName());
    }
}
