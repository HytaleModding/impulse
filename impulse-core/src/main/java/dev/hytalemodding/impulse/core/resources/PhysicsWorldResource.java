package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import java.util.logging.Level;

/**
 * ECS resource that holds the physics spaces for a world.
 */
public class PhysicsWorldResource implements Resource<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();
    private SpaceId mainSpaceId;

    @Setter
    @Getter
    private boolean debugEnabled;

    public PhysicsWorldResource() {
    }

    @Nonnull
    public PhysicsSpace getMainSpace() {
        // TODO: probably a static final name instead of hardcoded name
        return getMainSpace("<unknown>");
    }

    @Nonnull
    public PhysicsSpace getMainSpace(@Nonnull String worldName) {
        if (mainSpaceId == null) {
            BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
            LOGGER.at(Level.INFO).log(
                "World %s creating main physics space using backend %s",
                worldName,
                backendId);

            PhysicsSpace space = Impulse.createSpace(backendId);
            // FIXME: ad hoc Y value for testing
            space.addBody(space.createStaticPlane(122f));
            spaces.put(space.getId().value(), space);
            mainSpaceId = space.getId();

            LOGGER.at(Level.INFO).log(
                "World %s created main physics space id=%s backend=%s",
                worldName,
                mainSpaceId,
                space.getBackendId());
        }

        PhysicsSpace space = spaces.get(mainSpaceId.value());
        if (space == null) {
            throw new IllegalStateException("Main physics space is missing");
        }
        return space;
    }

    @Nonnull
    public SpaceId getMainSpaceId() {
        return getMainSpace().getId();
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId) {
        // TODO: add a command path that migrates an existing physics space to another backend.
        return createSpace(backendId, "<unknown>");
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        LOGGER.at(Level.FINE).log(
            "World %s creating additional physics space using backend %s",
            worldName,
            backendId);

        PhysicsSpace space = Impulse.createSpace(backendId);
        spaces.put(space.getId().value(), space);
        if (mainSpaceId == null) {
            mainSpaceId = space.getId();
        }

        LOGGER.at(Level.FINE).log(
            "World %s created additional physics space id=%s backend=%s",
            worldName,
            space.getId(),
            space.getBackendId());
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaces.get(spaceId.value());
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return getSpaces("<unknown>");
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces(@Nonnull String worldName) {
        getMainSpace(worldName);
        return new ArrayList<>(spaces.values());
    }

    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpace removed = spaces.remove(spaceId.value());
        if (mainSpaceId != null && mainSpaceId.equals(spaceId)) {
            mainSpaceId = null;
        }
        if (removed != null) {
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.getId(),
                removed.getBackendId());
        }
    }

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType()
    {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldResource copy = new PhysicsWorldResource();
        // FIXME: deepcopy?
        copy.spaces.putAll(spaces);
        copy.mainSpaceId = mainSpaceId;
        copy.debugEnabled = debugEnabled;
        return copy;
    }
}
