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
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * ECS resource that holds the physics spaces for a world.
 */
public class PhysicsWorldResource implements Resource<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    public static final int MIN_SIMULATION_STEPS = 1;
    public static final int MAX_SIMULATION_STEPS = 16;

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();
    private SpaceId mainSpaceId;
    @Getter
    private int simulationSteps = MIN_SIMULATION_STEPS;

    // TODO: there's probably a better place than debug flags here
    // TODO: switch to bitflags

    @Setter
    @Getter
    private boolean debugEnabled;

    @Setter
    @Getter
    private boolean debugShapesEnabled = true;

    @Setter
    @Getter
    private boolean debugMotionEnabled = true;

    @Setter
    @Getter
    private boolean debugContactsEnabled = true;

    @Setter
    @Getter
    private boolean debugJointsEnabled = true;

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

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces() {
        return iterateSpaces("<unknown>");
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces(@Nonnull String worldName) {
        getMainSpace(worldName);
        return spaces.values();
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

    /**
     * Set how many fixed substeps are run for each server tick.
     * Higher values can improve stability at the cost of backend step time.
     */
    public void setSimulationSteps(int simulationSteps) {
        if (simulationSteps < MIN_SIMULATION_STEPS || simulationSteps > MAX_SIMULATION_STEPS) {
            throw new IllegalArgumentException("Simulation steps must be between "
                + MIN_SIMULATION_STEPS + " and " + MAX_SIMULATION_STEPS);
        }
        this.simulationSteps = simulationSteps;
    }

    @Nonnull
    public PhysicsSpace replaceSpace(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        if (!spaceId.equals(replacement.getId())) {
            throw new IllegalArgumentException("Replacement space id " + replacement.getId()
                + " does not match target id " + spaceId);
        }

        PhysicsSpace previous = spaces.get(spaceId.value());
        if (previous == null) {
            throw new IllegalStateException("Cannot replace missing physics space id=" + spaceId);
        }

        spaces.put(spaceId.value(), replacement);
        LOGGER.at(Level.INFO).log(
            "World %s replaced physics space id=%s backend=%s -> backend=%s",
            worldName,
            spaceId,
            previous.getBackendId(),
            replacement.getBackendId());
        return previous;
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
        copy.simulationSteps = simulationSteps;
        copy.debugEnabled = debugEnabled;
        copy.debugShapesEnabled = debugShapesEnabled;
        copy.debugMotionEnabled = debugMotionEnabled;
        copy.debugContactsEnabled = debugContactsEnabled;
        copy.debugJointsEnabled = debugJointsEnabled;
        return copy;
    }
}
