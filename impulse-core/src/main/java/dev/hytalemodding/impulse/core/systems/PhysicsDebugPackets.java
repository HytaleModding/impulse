package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Targeted debug packet helpers used by Impulse debug overlays.
 *
 * <p>Hytale's DebugUtils broadcasts every debug primitive to every player.
 * This helper keeps packet routing explicit so Impulse can render overlays only
 * for opted-in viewers and avoid world-wide packet spam.</p>
 */
final class PhysicsDebugPackets {

    private PhysicsDebugPackets() {
    }

    static void clear(@Nonnull PlayerRef viewer) {
        viewer.getPacketHandler().write(new ClearDebugShapes());
    }

    static void add(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull DebugShape shape,
        @Nonnull Matrix4d matrix,
        @Nonnull Vector3f color,
        float opacity,
        float time,
        int flags) {
        add(viewers, shape, matrix, color, opacity, time, flags, null);
    }

    static void add(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull DebugShape shape,
        @Nonnull Matrix4d matrix,
        @Nonnull Vector3f color,
        float opacity,
        float time,
        int flags,
        @Nullable float[] shapeParams) {
        if (viewers.isEmpty()) {
            return;
        }

        DisplayDebug packet = new DisplayDebug(shape,
            Matrix4dUtil.asFloatData(matrix),
            color,
            time,
            (byte) flags,
            shapeParams,
            opacity);
        for (PlayerRef viewer : viewers) {
            viewer.getPacketHandler().write(packet);
        }
    }

    static void addSphere(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d position,
        @Nonnull Vector3f color,
        float opacity,
        double scale,
        float time,
        int flags) {
        Matrix4d matrix = new Matrix4d()
            .identity()
            .translate(position)
            .scale(scale, scale, scale);
        add(viewers, DebugShape.Sphere, matrix, color, opacity, time, flags);
    }

    static void addLine(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d start,
        @Nonnull Vector3d end,
        @Nonnull Vector3f color,
        double thickness,
        float opacity,
        float time,
        int flags) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) {
            return;
        }

        Matrix4d matrix = new Matrix4d().identity();
        matrix.translate(start);
        double angleY = Math.atan2(dz, dx);
        matrix.rotate(-(angleY + (Math.PI / 2.0)), 0.0, 1.0, 0.0);
        double angleX = Math.atan2(Math.sqrt(dx * dx + dz * dz), dy);
        matrix.rotate(-angleX, 1.0, 0.0, 0.0);
        matrix.translate(0.0, length / 2.0, 0.0);
        matrix.scale(thickness, length, thickness);
        add(viewers, DebugShape.Cylinder, matrix, color, opacity, time, flags);
    }

    static void addArrow(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d start,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color,
        float opacity,
        float time,
        int flags) {
        Vector3d vector = new Vector3d(direction);
        Matrix4d matrix = new Matrix4d().identity();
        matrix.translate(start);

        double angleY = Math.atan2(vector.z, vector.x);
        matrix.rotate(-(angleY + (Math.PI / 2.0)), 0.0, 1.0, 0.0);
        double angleX = Math.atan2(Math.sqrt(vector.x * vector.x + vector.z * vector.z), vector.y);
        matrix.rotate(-angleX, 1.0, 0.0, 0.0);

        double adjustedLength = vector.length() - 0.3;
        if (adjustedLength > 0.0) {
            Matrix4d shaft = new Matrix4d(matrix)
                .translate(0.0, adjustedLength * 0.5, 0.0)
                .scale(0.1f, adjustedLength, 0.1f);
            add(viewers, DebugShape.Cylinder, shaft, color, opacity, time, flags);
        }

        Matrix4d tip = new Matrix4d(matrix)
            .translate(0.0, adjustedLength + 0.15, 0.0)
            .scale(0.3f, 0.3f, 0.3f);
        add(viewers, DebugShape.Cone, tip, color, opacity, time, flags);
    }
}
