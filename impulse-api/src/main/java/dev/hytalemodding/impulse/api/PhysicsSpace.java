package dev.hytalemodding.impulse.api;

import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Live simulation container for a physics backend.
 * <ul>
 *     <li>This type must not be assumed to be thread-safe.</li>
 *     <li>All mutations and stepping must happen from a single owner thread.</li>
 *     <li>If other threads need to interact, queue commands onto the owner thread.</li>
 * </ul>
 */
public interface PhysicsSpace {

    @Nonnull
    SpaceId getId();

    @Nonnull
    BackendId getBackendId();

    void step(float dt);

    void setGravity(float x, float y, float z);

    void addBody(@Nonnull PhysicsBody body);

    void removeBody(@Nonnull PhysicsBody body);

    @Nonnull
    List<PhysicsBody> getBodies();

    @Nonnull
    PhysicsBody createStaticPlane(float groundY);

    @Nonnull
    PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass);

    @Nonnull
    PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass);
}
