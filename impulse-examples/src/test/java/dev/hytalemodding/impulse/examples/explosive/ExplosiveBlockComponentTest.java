package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExplosiveBlockComponentTest {

    @Test
    void clonePreservesGroupedExplosionState() {
        ExplosiveBlockComponent component = new ExplosiveBlockComponent(
            "Hytale:block/stone",
            1,
            3,
            4,
            48,
            18.0f,
            0.35f);

        ExplosiveBlockComponent copy = component.clone();

        assertEquals("Hytale:block/stone", copy.getBlockType());
        assertEquals(1, copy.getGeneration());
        assertEquals(3, copy.getMaxGeneration());
        assertEquals(4, copy.getRadius());
        assertEquals(48, copy.getMaxFragments());
        assertEquals(18.0f, copy.getImpulseStrength());
        assertEquals(0.35f, copy.getVerticalLift());
    }

    @Test
    void constructorKeepsRuntimeBoundsUsable() {
        ExplosiveBlockComponent component = new ExplosiveBlockComponent(
            "",
            -2,
            -1,
            -4,
            0,
            -12.0f,
            -0.5f);

        assertEquals(ExplosiveBlockPolicy.DEFAULT_BLOCK_TYPE, component.getBlockType());
        assertEquals(0, component.getGeneration());
        assertEquals(0, component.getMaxGeneration());
        assertEquals(1, component.getRadius());
        assertEquals(1, component.getMaxFragments());
        assertEquals(0.0f, component.getImpulseStrength());
        assertEquals(0.0f, component.getVerticalLift());
    }
}
