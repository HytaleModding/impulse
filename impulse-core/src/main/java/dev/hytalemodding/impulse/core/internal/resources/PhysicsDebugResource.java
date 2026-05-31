package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-only debug overlay state for one world EntityStore.
 *
 * <p>This resource intentionally keeps transient debug session state out of
 * {@link PhysicsWorldResource}. Physics world state is persisted and shared
 * by gameplay systems, while debug subscriptions, cadence, and packet budgets
 * are temporary operational concerns.</p>
 */
@Getter
public class PhysicsDebugResource implements Resource<EntityStore> {

    public static final float MIN_REFRESH_SECONDS = 0.05f;
    public static final float MAX_REFRESH_SECONDS = 2.0f;
    public static final float DEFAULT_OVERLAY_REFRESH_SECONDS = 0.10f;
    public static final float DEFAULT_WORLD_COLLISION_REFRESH_SECONDS = 0.25f;

    public static final double DEFAULT_VIEW_RADIUS = 96.0;
    public static final int DEFAULT_MAX_BODIES = 512;
    public static final int DEFAULT_MAX_CONTACTS = 384;
    public static final int DEFAULT_MAX_JOINTS = 384;
    public static final int DEFAULT_MAX_WORLD_COLLISION_SECTIONS = 192;
    public static final int DEFAULT_MAX_WORLD_COLLISION_BOXES = 768;

    private final Set<UUID> subscriberUuids = new ObjectOpenHashSet<>();

    @Setter
    private boolean debugShapesEnabled = true;
    @Setter
    private boolean debugMotionEnabled = true;
    @Setter
    private boolean debugContactsEnabled = true;
    @Setter
    private boolean debugJointsEnabled = true;
    @Setter
    private boolean debugWorldCollisionEnabled;

    private float overlayRefreshSeconds = DEFAULT_OVERLAY_REFRESH_SECONDS;
    private float worldCollisionRefreshSeconds = DEFAULT_WORLD_COLLISION_REFRESH_SECONDS;
    private float overlayTimeUntilRefresh;
    private float worldCollisionTimeUntilRefresh;

    private double viewRadius = DEFAULT_VIEW_RADIUS;
    private int maxBodies = DEFAULT_MAX_BODIES;
    private int maxContacts = DEFAULT_MAX_CONTACTS;
    private int maxJoints = DEFAULT_MAX_JOINTS;
    private int maxWorldCollisionSections = DEFAULT_MAX_WORLD_COLLISION_SECTIONS;
    private int maxWorldCollisionBoxes = DEFAULT_MAX_WORLD_COLLISION_BOXES;

    public PhysicsDebugResource() {
    }

    public boolean addSubscriber(@Nonnull UUID uuid) {
        return subscriberUuids.add(uuid);
    }

    public boolean removeSubscriber(@Nonnull UUID uuid) {
        return subscriberUuids.remove(uuid);
    }

    public void clearSubscribers() {
        subscriberUuids.clear();
    }

    @Nonnull
    public Set<UUID> getSubscriberUuids() {
        return Set.copyOf(subscriberUuids);
    }

    public boolean hasSubscribers() {
        return !subscriberUuids.isEmpty();
    }

    public void setOverlayRefreshSeconds(float overlayRefreshSeconds) {
        this.overlayRefreshSeconds = clampRefresh(overlayRefreshSeconds);
    }

    public void setWorldCollisionRefreshSeconds(float worldCollisionRefreshSeconds) {
        this.worldCollisionRefreshSeconds = clampRefresh(worldCollisionRefreshSeconds);
    }

    public void setViewRadius(double viewRadius) {
        this.viewRadius = Math.max(1.0, viewRadius);
    }

    public void setMaxBodies(int maxBodies) {
        this.maxBodies = Math.max(1, maxBodies);
    }

    public void setMaxContacts(int maxContacts) {
        this.maxContacts = Math.max(1, maxContacts);
    }

    public void setMaxJoints(int maxJoints) {
        this.maxJoints = Math.max(1, maxJoints);
    }

    public void setMaxWorldCollisionSections(int maxWorldCollisionSections) {
        this.maxWorldCollisionSections = Math.max(1, maxWorldCollisionSections);
    }

    public void setMaxWorldCollisionBoxes(int maxWorldCollisionBoxes) {
        this.maxWorldCollisionBoxes = Math.max(1, maxWorldCollisionBoxes);
    }

    public boolean tickOverlayBudget(float dt) {
        overlayTimeUntilRefresh -= dt;
        if (overlayTimeUntilRefresh > 0.0f) {
            return false;
        }

        overlayTimeUntilRefresh += overlayRefreshSeconds;
        if (overlayTimeUntilRefresh <= 0.0f) {
            overlayTimeUntilRefresh = overlayRefreshSeconds;
        }
        return true;
    }

    public boolean tickWorldCollisionBudget(float dt) {
        worldCollisionTimeUntilRefresh -= dt;
        if (worldCollisionTimeUntilRefresh > 0.0f) {
            return false;
        }

        worldCollisionTimeUntilRefresh += worldCollisionRefreshSeconds;
        if (worldCollisionTimeUntilRefresh <= 0.0f) {
            worldCollisionTimeUntilRefresh = worldCollisionRefreshSeconds;
        }
        return true;
    }

    @Nonnull
    @Override
    public PhysicsDebugResource clone() {
        PhysicsDebugResource copy = new PhysicsDebugResource();
        copy.subscriberUuids.addAll(subscriberUuids);
        copy.debugShapesEnabled = debugShapesEnabled;
        copy.debugMotionEnabled = debugMotionEnabled;
        copy.debugContactsEnabled = debugContactsEnabled;
        copy.debugJointsEnabled = debugJointsEnabled;
        copy.debugWorldCollisionEnabled = debugWorldCollisionEnabled;
        copy.overlayRefreshSeconds = overlayRefreshSeconds;
        copy.worldCollisionRefreshSeconds = worldCollisionRefreshSeconds;
        copy.overlayTimeUntilRefresh = overlayTimeUntilRefresh;
        copy.worldCollisionTimeUntilRefresh = worldCollisionTimeUntilRefresh;
        copy.viewRadius = viewRadius;
        copy.maxBodies = maxBodies;
        copy.maxContacts = maxContacts;
        copy.maxJoints = maxJoints;
        copy.maxWorldCollisionSections = maxWorldCollisionSections;
        copy.maxWorldCollisionBoxes = maxWorldCollisionBoxes;
        return copy;
    }

    public static ResourceType<EntityStore, PhysicsDebugResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsDebugResourceType();
    }

    private static float clampRefresh(float value) {
        return Math.clamp(value, MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS);
    }
}
