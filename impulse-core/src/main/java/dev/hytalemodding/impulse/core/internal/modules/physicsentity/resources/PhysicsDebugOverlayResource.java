package dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Runtime-only debug overlay projection state for one world EntityStore.
 *
 * <p>Physics debug flags live on PhysicsStore. This resource owns only viewer subscription,
 * projection cadence, and packet-budget state for rendering copied physics debug views.</p>
 */
@Getter
public class PhysicsDebugOverlayResource implements Resource<EntityStore> {

    @Getter
    @Nullable
    private static ResourceType<EntityStore, PhysicsDebugOverlayResource> resourceType;

    public static final float MIN_REFRESH_SECONDS = 0.05f;
    public static final float MAX_REFRESH_SECONDS = 2.0f;
    public static final float DEFAULT_OVERLAY_REFRESH_SECONDS = 0.10f;
    public static final float DEFAULT_PHYSICS_CHUNK_REFRESH_SECONDS = 0.25f;

    public static final double DEFAULT_VIEW_RADIUS = 96.0;
    public static final int DEFAULT_MAX_BODIES = 512;
    public static final int DEFAULT_MAX_CONTACTS = 384;
    public static final int DEFAULT_MAX_JOINTS = 384;
    public static final int DEFAULT_MAX_PHYSICS_CHUNK_SECTIONS = 192;
    public static final int DEFAULT_MAX_PHYSICS_CHUNK_BOXES = 768;

    private final Set<UUID> subscriberUuids = new ObjectOpenHashSet<>();

    private float overlayRefreshSeconds = DEFAULT_OVERLAY_REFRESH_SECONDS;
    private float physicsChunkRefreshSeconds = DEFAULT_PHYSICS_CHUNK_REFRESH_SECONDS;
    private float overlayTimeUntilRefresh;
    private float physicsChunkTimeUntilRefresh;

    private double viewRadius = DEFAULT_VIEW_RADIUS;
    private int maxBodies = DEFAULT_MAX_BODIES;
    private int maxContacts = DEFAULT_MAX_CONTACTS;
    private int maxJoints = DEFAULT_MAX_JOINTS;
    private int maxPhysicsChunkSections = DEFAULT_MAX_PHYSICS_CHUNK_SECTIONS;
    private int maxPhysicsChunkBoxes = DEFAULT_MAX_PHYSICS_CHUNK_BOXES;

    public PhysicsDebugOverlayResource() {
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

    public void setPhysicsChunkRefreshSeconds(float physicsChunkRefreshSeconds) {
        this.physicsChunkRefreshSeconds = clampRefresh(physicsChunkRefreshSeconds);
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

    public void setMaxPhysicsChunkSections(int maxPhysicsChunkSections) {
        this.maxPhysicsChunkSections = Math.max(1, maxPhysicsChunkSections);
    }

    public void setMaxPhysicsChunkBoxes(int maxPhysicsChunkBoxes) {
        this.maxPhysicsChunkBoxes = Math.max(1, maxPhysicsChunkBoxes);
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

    public boolean tickPhysicsChunkBudget(float dt) {
        physicsChunkTimeUntilRefresh -= dt;
        if (physicsChunkTimeUntilRefresh > 0.0f) {
            return false;
        }

        physicsChunkTimeUntilRefresh += physicsChunkRefreshSeconds;
        if (physicsChunkTimeUntilRefresh <= 0.0f) {
            physicsChunkTimeUntilRefresh = physicsChunkRefreshSeconds;
        }
        return true;
    }

    @Nonnull
    @Override
    public PhysicsDebugOverlayResource clone() {
        PhysicsDebugOverlayResource copy = new PhysicsDebugOverlayResource();
        copy.subscriberUuids.addAll(subscriberUuids);
        copy.overlayRefreshSeconds = overlayRefreshSeconds;
        copy.physicsChunkRefreshSeconds = physicsChunkRefreshSeconds;
        copy.overlayTimeUntilRefresh = overlayTimeUntilRefresh;
        copy.physicsChunkTimeUntilRefresh = physicsChunkTimeUntilRefresh;
        copy.viewRadius = viewRadius;
        copy.maxBodies = maxBodies;
        copy.maxContacts = maxContacts;
        copy.maxJoints = maxJoints;
        copy.maxPhysicsChunkSections = maxPhysicsChunkSections;
        copy.maxPhysicsChunkBoxes = maxPhysicsChunkBoxes;
        return copy;
    }

    public static void setResourceType(
        @Nonnull ResourceType<EntityStore, PhysicsDebugOverlayResource> type) {
        resourceType = type;
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    private static float clampRefresh(float value) {
        return Math.clamp(value, MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS);
    }
}
