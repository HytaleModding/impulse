package dev.hytalemodding.impulse.core.plugin.resources;

/**
 * Mutation that must run on the thread currently owning live physics backend state.
 *
 * <p>The callback may mutate Impulse backend objects such as live physics bodies and spaces. It
 * must not access Hytale ECS store state unless the caller can prove that access is valid from the
 * physics owner thread.</p>
 */
@FunctionalInterface
public interface PhysicsOwnerMutation {

    void run() throws Exception;
}
