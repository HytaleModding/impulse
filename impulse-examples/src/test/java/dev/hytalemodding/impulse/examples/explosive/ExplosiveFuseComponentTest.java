package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ExplosiveFuseComponentTest {

    @Test
    void armStartsOneSecondFuseOnlyOnce() {
        ExplosiveFuseComponent fuse = new ExplosiveFuseComponent();

        assertFalse(fuse.isDue(119L));

        assertTrue(fuse.arm(100L));
        assertFalse(fuse.isDue(119L));
        assertTrue(fuse.isDue(120L));

        assertFalse(fuse.arm(105L));
        assertTrue(fuse.isDue(120L));
    }

    @Test
    void verticalVelocityObservationArmsAfterFirstBounce() {
        ExplosiveFuseComponent fuse = new ExplosiveFuseComponent();

        assertFalse(fuse.observeVerticalVelocity(-0.1f, 10L));
        assertTrue(fuse.observeVerticalVelocity(-0.4f, 11L));
        assertFalse(fuse.isDue(31L));

        Vector3d bounceCenter = new Vector3d(4.5, 8.5, -2.5);
        assertTrue(fuse.observeVerticalVelocity(0.15f, 12L, bounceCenter));
        assertFalse(fuse.isDue(31L));
        assertTrue(fuse.isDue(32L));
        assertEquals(bounceCenter, fuse.explosionCenterOr(new Vector3d()));
        assertFalse(fuse.observeVerticalVelocity(0.2f, 13L));
    }

    @Test
    void verticalVelocityObservationArmsWhenFallingBodySettlesWithoutBounce() {
        ExplosiveFuseComponent fuse = new ExplosiveFuseComponent();

        assertTrue(fuse.observeVerticalVelocity(-0.4f, 11L));

        Vector3d settledCenter = new Vector3d(4.5, 8.5, -2.5);
        assertTrue(fuse.observeVerticalVelocity(0.0f, 12L, settledCenter));
        assertFalse(fuse.isDue(31L));
        assertTrue(fuse.isDue(32L));
        assertEquals(settledCenter, fuse.explosionCenterOr(new Vector3d()));
    }
}
