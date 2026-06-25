package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.builtin.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.builtin.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import dev.hytalemodding.impulse.examples.utils.ExampleBlockEntityVisuals;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Spawn a PhysicsStore body row with an attached visible block entity.
 */
public class DropCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Hytale block type used for the attached visual entity",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public DropCommand() {
        super("drop", "Spawn a physics box that falls under gravity");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Vector3d playerPos = playerRef.getTransform().getPosition();
        float spawnX = (float) playerPos.x();
        float spawnY = (float) playerPos.y() + 5f;
        float spawnZ = (float) playerPos.z();
        Vector3d position = new Vector3d(spawnX, spawnY, spawnZ);

        ExamplePhysicsUtils.SpaceSelection space = ExamplePhysicsUtils.spaceSelection(ctx,
            world,
            spaceArg);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(physicsStore,
            "spawn an example PhysicsStore body entity");
        UUID bodyUuid = UUID.randomUUID();
        Holder<PhysicsStore> bodyHolder = PhysicsBodyEntities.dynamicBodyHolder(space.spaceRef(),
            bodyUuid,
            toVector3f(position),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            RigidBodySpawnSettings.material(0.5f, 0.5f),
            null);
        Ref<PhysicsStore> bodyRef = physicsStore.addEntity(bodyHolder, AddReason.SPAWN);

        assert bodyRef != null;
        store.addEntity(attachedPhysicsBlockEntityHolder(time,
            bodyRef,
            bodyUuid,
            blockType(ctx),
            position,
            new Vector3f(),
            new Quaternionf(),
            Float.NaN,
            true),
            AddReason.SPAWN);

        ctx.sender()
            .sendMessage(Message.raw("Dropped box at " + spawnX + ", " + spawnY + ", " + spawnZ));

        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private String blockType(@Nonnull CommandContext ctx) {
        return blockTypeArg.provided(ctx)
            ? ExampleBlockEntityVisuals.resolveBlockType(blockTypeArg.get(ctx))
            : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    }

    @Nonnull
    private static Holder<EntityStore> attachedPhysicsBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull String blockType,
        @Nonnull Vector3d position,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        requirePhysicsEntityVisuals();
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(time,
            ExampleBlockEntityVisuals.resolveBlockType(blockType),
            new Vector3d(position));
        holder.tryRemoveComponent(DespawnComponent.getComponentType());
        holder.tryRemoveComponent(Velocity.getComponentType());

        BodyAttachmentComponent attachment = BodyAttachmentComponent.impulseOwnedVisual(bodyUuid,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
        attachment.setBodyRef(bodyRef);
        holder.addComponent(BodyAttachmentComponent.getComponentType(), attachment);
        if (controllable && PhysicsControlSessions.isAvailable()) {
            holder.addComponent(ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
        return holder;
    }

    private static void requirePhysicsEntityVisuals() {
        if (!PhysicsEntityAttachments.isAvailable()) {
            throw new IllegalStateException(
                "Impulse PhysicsEntity integration is not available. "
                    + "Enable HytaleModding:ImpulsePhysicsEntity to spawn entity-backed example visuals.");
        }
    }

    @Nonnull
    private static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
