package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

class ExplosiveBlockPolicyTest {

    @Test
    void outwardImpulsePushesAwayFromExplosionWithVerticalLift() {
        Vector3f impulse = ExplosiveBlockPolicy.outwardImpulse(
            new Vector3f(0.5f, 10.5f, 0.5f),
            new Vector3f(2.5f, 10.5f, 0.5f),
            12.0f,
            0.35f);

        assertTrue(impulse.x > 11.0f);
        assertTrue(impulse.y > 3.0f);
        assertEquals(0.0f, impulse.z, 0.0001f);
    }

    @Test
    void centeredBlocksStillReceiveUpwardImpulse() {
        Vector3f impulse = ExplosiveBlockPolicy.outwardImpulse(
            new Vector3f(4.5f, 8.5f, 4.5f),
            new Vector3f(4.5f, 8.5f, 4.5f),
            6.0f,
            0.5f);

        assertEquals(0.0f, impulse.x, 0.0001f);
        assertEquals(6.0f, impulse.y, 0.0001f);
        assertEquals(0.0f, impulse.z, 0.0001f);
    }

    @Test
    void chainGenerationStopsAtConfiguredCap() {
        assertTrue(ExplosiveBlockPolicy.shouldChain(0, 2));
        assertTrue(ExplosiveBlockPolicy.shouldChain(1, 2));
        assertFalse(ExplosiveBlockPolicy.shouldChain(2, 2));
        assertFalse(ExplosiveBlockPolicy.shouldChain(3, 2));
    }

    @Test
    void ignoresAirAndUnknownBlocksWhenCreatingFragments() {
        assertFalse(ExplosiveBlockPolicy.isFragmentCandidate(0));
        assertFalse(ExplosiveBlockPolicy.isFragmentCandidate(1));
        assertTrue(ExplosiveBlockPolicy.isFragmentCandidate(42));
    }

    @Test
    void landingBlockPositionRoundsToContainingBlock() {
        Vector3i block = ExplosiveBlockPolicy.landingBlockPosition(new Vector3f(12.8f, 7.2f, -3.1f));

        assertEquals(new Vector3i(12, 7, -4), block);
    }
}
