package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RaycastHitViewTest {

    @Test
    void storesGeometryAsScalarsWhileKeepingDefensiveVectorAccessors() {
        Vector3f point = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);
        RaycastHitView view = new RaycastHitView(null,
            PhysicsBodyType.DYNAMIC,
            point,
            normal,
            ShapeType.BOX,
            0.25f,
            4.5f);
        point.set(9.0f, 9.0f, 9.0f);
        normal.set(8.0f, 8.0f, 8.0f);

        assertEquals(1.0f, view.pointX(), 0.00001f);
        assertEquals(2.0f, view.pointY(), 0.00001f);
        assertEquals(3.0f, view.pointZ(), 0.00001f);
        assertEquals(0.0f, view.normalX(), 0.00001f);
        assertEquals(1.0f, view.normalY(), 0.00001f);
        assertEquals(0.0f, view.normalZ(), 0.00001f);

        Vector3f pointCopy = view.point();
        pointCopy.set(7.0f, 7.0f, 7.0f);
        assertEquals(1.0f, view.pointX(), 0.00001f);
        assertEquals(2.0f, view.pointY(), 0.00001f);
        assertEquals(3.0f, view.pointZ(), 0.00001f);
        assertNotSame(pointCopy, view.point());

        Vector3f target = new Vector3f();
        assertSame(target, view.copyPointTo(target));
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), target);
        assertSame(target, view.copyNormalTo(target));
        assertEquals(new Vector3f(0.0f, 1.0f, 0.0f), target);
    }
}
