package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;

/**
 * Codec-backed quaternion for the Impulse persistence layer.
 *
 * <p>JOML's {@link Quaternionf} does not implement Hytale's codec interface,
 * so this class wraps the same (x, y, z, w) components in a mutable
 * BuilderCodec-compatible container. The {@link #CODEC} field handles
 * serialization and deserialization; the {@code of} and {@code toQuaternionf}
 * bridge methods convert between this and the live JOML type.</p>
 */
@Getter
@Setter
public final class PersistentQuaternion {

    @Nonnull
    public static final BuilderCodec<PersistentQuaternion> CODEC = BuilderCodec.builder(
            PersistentQuaternion.class,
            PersistentQuaternion::new)
        .append(new KeyedCodec<>("X", Codec.FLOAT), (value, x) -> value.x = x, PersistentQuaternion::getX)
        .add()
        .append(new KeyedCodec<>("Y", Codec.FLOAT), (value, y) -> value.y = y, PersistentQuaternion::getY)
        .add()
        .append(new KeyedCodec<>("Z", Codec.FLOAT), (value, z) -> value.z = z, PersistentQuaternion::getZ)
        .add()
        .append(new KeyedCodec<>("W", Codec.FLOAT), (value, w) -> value.w = w, PersistentQuaternion::getW)
        .add()
        .build();

    private float x;
    private float y;
    private float z;
    private float w = 1.0f;

    public PersistentQuaternion() {
    }

    public PersistentQuaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    @Nonnull
    public static PersistentQuaternion of(@Nonnull Quaternionf quaternion) {
        return new PersistentQuaternion(quaternion.x, quaternion.y, quaternion.z, quaternion.w);
    }

    public void set(@Nonnull Quaternionf quaternion) {
        x = quaternion.x;
        y = quaternion.y;
        z = quaternion.z;
        w = quaternion.w;
    }

    @Nonnull
    public Quaternionf toQuaternionf() {
        return new Quaternionf(x, y, z, w);
    }

    @Nonnull
    public PersistentQuaternion copy() {
        return new PersistentQuaternion(x, y, z, w);
    }
}
