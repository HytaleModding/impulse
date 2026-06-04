package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockRuntime;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

public final class ExplosiveBlockSystem
    extends WorldEventSystem<EntityStore, PhysicsEventFramePublishedEvent> {

    public ExplosiveBlockSystem() {
        super(PhysicsEventFramePublishedEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsEventFramePublishedEvent event) {
        if (!ExplosiveBlockComponent.isComponentTypeRegistered()) {
            return;
        }
        PhysicsWorldResource resource = commandBuffer.getResource(PhysicsWorldResource.getResourceType());
        TimeResource time = commandBuffer.getResource(TimeResource.getResourceType());
        World world = commandBuffer.getExternalData().getWorld();
        Set<RigidBodyKey> settledBodies = new ObjectOpenHashSet<>();

        for (PhysicsFrameEvent frameEvent : event.frame().physicsEvents()) {
            if (frameEvent instanceof PhysicsContactEvent contactEvent) {
                settleContact(commandBuffer, resource, time, world, contactEvent, settledBodies);
            }
        }
    }

    private static void settleContact(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull TimeResource time,
        @Nonnull World world,
        @Nonnull PhysicsContactEvent contact,
        @Nonnull Set<RigidBodyKey> settledBodies) {
        if (contact.phase() == PhysicsContactPhase.ENDED) {
            return;
        }

        ExplosiveContact bodyA = explosiveContact(commandBuffer, resource, contact.bodyAKey());
        ExplosiveContact bodyB = explosiveContact(commandBuffer, resource, contact.bodyBKey());
        if (bodyA != null && bodyB != null) {
            return;
        }
        if (bodyA != null && isWorldCollision(resource, contact.bodyBKey())) {
            settle(commandBuffer, resource, time, world, contact.spaceId(), bodyA, contact.pointOnA(),
                settledBodies);
        } else if (bodyB != null && isWorldCollision(resource, contact.bodyAKey())) {
            settle(commandBuffer, resource, time, world, contact.spaceId(), bodyB, contact.pointOnB(),
                settledBodies);
        }
    }

    private static void settle(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull TimeResource time,
        @Nonnull World world,
        @Nonnull SpaceId eventSpaceId,
        @Nonnull ExplosiveContact contact,
        @Nonnull Vector3f contactPoint,
        @Nonnull Set<RigidBodyKey> settledBodies) {
        if (!settledBodies.add(contact.bodyKey())) {
            return;
        }
        ExplosiveBlockRuntime.settleAndMaybeChain(commandBuffer,
            world,
            time,
            resource,
            contact.spaceId() != null ? contact.spaceId() : eventSpaceId,
            contact.bodyKey(),
            contact.component(),
            contactPoint);
        commandBuffer.removeEntity(contact.entity(), RemoveReason.REMOVE);
    }

    @Nullable
    private static ExplosiveContact explosiveContact(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull RigidBodyKey bodyKey) {
        ComponentType<EntityStore, ExplosiveBlockComponent> explosiveType =
            ExplosiveBlockComponent.getComponentType();
        ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType =
            PhysicsBodyAttachmentComponent.getComponentType();
        for (Ref<EntityStore> ref : resource.getBodyAttachments(bodyKey)) {
            if (!ref.isValid()) {
                continue;
            }
            ExplosiveBlockComponent component = commandBuffer.getComponent(ref, explosiveType);
            if (component == null) {
                continue;
            }
            PhysicsBodyAttachmentComponent attachment = commandBuffer.getComponent(ref, attachmentType);
            if (attachment == null || !bodyKey.equals(attachment.getBodyKey())) {
                continue;
            }
            return new ExplosiveContact(ref, bodyKey, attachment.getSpaceId(), component);
        }
        return null;
    }

    private static boolean isWorldCollision(@Nonnull PhysicsWorldResource resource,
        @Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistrationView registration = resource.getBodyRegistrationView(bodyKey);
        return registration != null && registration.kind() == PhysicsBodyKind.WORLD_COLLISION;
    }

    private record ExplosiveContact(@Nonnull Ref<EntityStore> entity,
                                    @Nonnull RigidBodyKey bodyKey,
                                    @Nullable SpaceId spaceId,
                                    @Nonnull ExplosiveBlockComponent component) {
    }
}
