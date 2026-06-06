package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import lombok.Getter;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExplosiveFuseComponent implements Component<EntityStore> {

    public static final long DEFAULT_FUSE_TICKS = 20L;
    private static final float FALLING_VELOCITY_THRESHOLD = -0.25f;
    // Contact events are the primary impact signal. This velocity fallback covers
    // impacts that settle without a visible bounce or without a published contact.
    private static final float SETTLED_VELOCITY_THRESHOLD = -0.05f;

    @Nonnull
    public static final BuilderCodec<ExplosiveFuseComponent> CODEC = BuilderCodec.builder(
            ExplosiveFuseComponent.class,
            ExplosiveFuseComponent::new)
        .append(new KeyedCodec<>("Armed", Codec.BOOLEAN, false),
            (component, value) -> component.armed = value != null && value,
            ExplosiveFuseComponent::isArmed)
        .add()
        .append(new KeyedCodec<>("TriggerTick", Codec.LONG, false),
            (component, value) -> component.triggerTick = nonNegative(value),
            ExplosiveFuseComponent::getTriggerTick)
        .add()
        .append(new KeyedCodec<>("ObservedDownwardMotion", Codec.BOOLEAN, false),
            (component, value) -> component.observedDownwardMotion = value != null && value,
            ExplosiveFuseComponent::hasObservedDownwardMotion)
        .add()
        .append(new KeyedCodec<>("HasExplosionCenter", Codec.BOOLEAN, false),
            (component, value) -> component.hasExplosionCenter = value != null && value,
            ExplosiveFuseComponent::hasExplosionCenter)
        .add()
        .append(new KeyedCodec<>("ExplosionCenterX", Codec.FLOAT, false),
            (component, value) -> component.explosionCenterX = finiteFloat(value),
            component -> component.explosionCenterX)
        .add()
        .append(new KeyedCodec<>("ExplosionCenterY", Codec.FLOAT, false),
            (component, value) -> component.explosionCenterY = finiteFloat(value),
            component -> component.explosionCenterY)
        .add()
        .append(new KeyedCodec<>("ExplosionCenterZ", Codec.FLOAT, false),
            (component, value) -> component.explosionCenterZ = finiteFloat(value),
            component -> component.explosionCenterZ)
        .add()
        .build();

    @Nullable
    private static ComponentType<EntityStore, ExplosiveFuseComponent> componentType;

    @Getter
    private boolean armed;
    @Getter
    private long triggerTick;
    private boolean observedDownwardMotion;
    private boolean hasExplosionCenter;
    private float explosionCenterX;
    private float explosionCenterY;
    private float explosionCenterZ;

    public ExplosiveFuseComponent() {
    }

    public ExplosiveFuseComponent(boolean armed, long triggerTick) {
        this(armed, triggerTick, false);
    }

    public ExplosiveFuseComponent(boolean armed, long triggerTick, boolean observedDownwardMotion) {
        this(armed, triggerTick, observedDownwardMotion, false, 0.0f, 0.0f, 0.0f);
    }

    public ExplosiveFuseComponent(boolean armed,
        long triggerTick,
        boolean observedDownwardMotion,
        boolean hasExplosionCenter,
        float explosionCenterX,
        float explosionCenterY,
        float explosionCenterZ) {
        this.armed = armed;
        this.triggerTick = Math.max(0L, triggerTick);
        this.observedDownwardMotion = observedDownwardMotion;
        this.hasExplosionCenter = hasExplosionCenter;
        this.explosionCenterX = explosionCenterX;
        this.explosionCenterY = explosionCenterY;
        this.explosionCenterZ = explosionCenterZ;
    }

    public boolean arm(long currentTick) {
        return arm(currentTick, null);
    }

    public boolean arm(long currentTick, @Nullable org.joml.Vector3d explosionCenter) {
        if (armed) {
            return false;
        }
        armed = true;
        triggerTick = Math.max(0L, currentTick) + DEFAULT_FUSE_TICKS;
        storeExplosionCenter(explosionCenter);
        return true;
    }

    public boolean isDue(long currentTick) {
        return armed && Math.max(0L, currentTick) >= triggerTick;
    }

    public boolean hasObservedDownwardMotion() {
        return observedDownwardMotion;
    }

    public boolean observeVerticalVelocity(float velocityY, long currentTick) {
        return observeVerticalVelocity(velocityY, currentTick, null);
    }

    public boolean observeVerticalVelocity(float velocityY,
        long currentTick,
        @Nullable org.joml.Vector3d explosionCenter) {
        if (armed || !Float.isFinite(velocityY)) {
            return false;
        }
        if (velocityY <= FALLING_VELOCITY_THRESHOLD) {
            if (observedDownwardMotion) {
                return false;
            }
            observedDownwardMotion = true;
            return true;
        }
        if (observedDownwardMotion && velocityY >= SETTLED_VELOCITY_THRESHOLD) {
            return arm(currentTick, explosionCenter);
        }
        return false;
    }

    public boolean hasExplosionCenter() {
        return hasExplosionCenter;
    }

    @Nonnull
    public org.joml.Vector3d explosionCenterOr(@Nonnull org.joml.Vector3d fallback) {
        if (!hasExplosionCenter) {
            return new org.joml.Vector3d(fallback);
        }
        return new org.joml.Vector3d(explosionCenterX, explosionCenterY, explosionCenterZ);
    }

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, ExplosiveFuseComponent> type) {
        componentType = Objects.requireNonNull(type, "type");
    }

    public static boolean isComponentTypeRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, ExplosiveFuseComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("Explosive fuse component is not registered");
        }
        return componentType;
    }

    @Nonnull
    @Override
    public ExplosiveFuseComponent clone() {
        return new ExplosiveFuseComponent(armed,
            triggerTick,
            observedDownwardMotion,
            hasExplosionCenter,
            explosionCenterX,
            explosionCenterY,
            explosionCenterZ);
    }

    private static long nonNegative(@Nullable Long value) {
        return Math.max(0L, value != null ? value : 0L);
    }

    private static float finiteFloat(@Nullable Float value) {
        return value != null && Float.isFinite(value) ? value : 0.0f;
    }

    private void storeExplosionCenter(@Nullable org.joml.Vector3d explosionCenter) {
        if (explosionCenter == null
            || !Double.isFinite(explosionCenter.x)
            || !Double.isFinite(explosionCenter.y)
            || !Double.isFinite(explosionCenter.z)) {
            return;
        }
        hasExplosionCenter = true;
        explosionCenterX = (float) explosionCenter.x;
        explosionCenterY = (float) explosionCenter.y;
        explosionCenterZ = (float) explosionCenter.z;
    }
}
