package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockRuntime;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExplosiveFuseContactSystem
    extends WorldEventSystem<EntityStore, PhysicsEventFramePublishedEvent> {

    private static final ComponentType<EntityStore, ExplosiveBlockComponent> EXPLOSIVE_TYPE =
        ExplosiveBlockComponent.getComponentType();
    private static final ComponentType<EntityStore, ExplosiveFuseComponent> FUSE_TYPE =
        ExplosiveFuseComponent.getComponentType();
    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();

    public ExplosiveFuseContactSystem() {
        super(PhysicsEventFramePublishedEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsEventFramePublishedEvent event) {
        Store<PhysicsStore> physicsStore =
            PhysicsThreading.store(store.getExternalData().getWorld());
        long tick = Math.max(0L, store.getExternalData().getWorld().getTick());
        for (PhysicsFrameEvent frameEvent : event.frame().physicsEvents()) {
            if (frameEvent instanceof PhysicsContactEvent contact
                && contact.phase() != PhysicsContactPhase.ENDED) {
                armIfExplosiveTouchesWorld(commandBuffer,
                    store,
                    physicsStore,
                    tick,
                    contact.bodyAUuid(),
                    contact.bodyBUuid(),
                    contactCenter(contact.pointOnB()));
                armIfExplosiveTouchesWorld(commandBuffer,
                    store,
                    physicsStore,
                    tick,
                    contact.bodyBUuid(),
                    contact.bodyAUuid(),
                    contactCenter(contact.pointOnA()));
            }
        }
    }

    private static void armIfExplosiveTouchesWorld(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
        long tick,
        @Nonnull UUID explosiveBodyUuid,
        @Nonnull UUID otherBodyUuid,
        @Nonnull Vector3d explosionCenter) {
        if (!isWorldCollision(physicsStore, otherBodyUuid)) {
            return;
        }
        for (Ref<EntityStore> ref : PhysicsEntityAttachments.attachments(store, explosiveBodyUuid)) {
            BodyAttachmentComponent attachment = commandBuffer.getComponent(ref, ATTACHMENT_TYPE);
            ExplosiveBlockComponent explosive = commandBuffer.getComponent(ref, EXPLOSIVE_TYPE);
            ExplosiveFuseComponent fuse = commandBuffer.getComponent(ref, FUSE_TYPE);
            if (attachment == null
                || explosive == null
                || fuse == null
                || !explosiveBodyUuid.equals(attachment.getBodyUuid())) {
                continue;
            }
            ExplosiveFuseComponent updated = fuse.clone();
            if (updated.arm(tick, explosionCenter)) {
                commandBuffer.putComponent(ref, FUSE_TYPE, updated);
            }
        }
    }

    private static boolean isWorldCollision(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull UUID bodyUuid) {
        PhysicsBodyRegistrationView registration = PhysicsBodies.registrationView(physicsStore,
            bodyUuid);
        return registration != null && registration.kind() == PhysicsBodyKind.WORLD_COLLISION;
    }

    @Nonnull
    private static Vector3d contactCenter(@Nonnull Vector3f contactPoint) {
        return ExplosiveBlockRuntime.contactExplosionCenter(new Vector3d(contactPoint.x,
            contactPoint.y,
            contactPoint.z));
    }
}
