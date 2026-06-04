package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.fallingblocks.FallingBlockSettings;
import dev.hytalemodding.impulse.api.SpaceId;
import org.junit.jupiter.api.Test;

class ImpulseExplosiveFallingBlockImpactTest {

    @Test
    void fallingBlockSettingsCarryImpulseExplosionStateIntoImpact() {
        ExplosiveBlockComponent component = new ExplosiveBlockComponent(
            "Hytale:block/stone",
            0,
            2,
            4,
            64,
            18.0f,
            0.5f);

        FallingBlockSettings settings = ImpulseExplosiveFallingBlockImpact.fallingBlockSettings(
            new SpaceId(7),
            component);

        ImpulseExplosiveFallingBlockImpact impact = assertInstanceOf(
            ImpulseExplosiveFallingBlockImpact.class,
            settings.getImpact());
        assertEquals(new SpaceId(7), impact.spaceId());
        assertEquals("Hytale:block/stone", impact.settings().getBlockType());
        assertEquals(4, impact.settings().getRadius());
        assertEquals(64, impact.settings().getMaxFragments());
        assertEquals(18.0f, impact.settings().getImpulseStrength());
        assertEquals(0.5f, impact.settings().getVerticalLift());
    }
}
