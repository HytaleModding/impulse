package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dynamic runtime body state overlaid from the latest completed backend snapshot.
 */
public final class PersistentBodyRuntimeStateDto {

    private static final Vector3f ZERO = new Vector3f();
    private static final Quaternionf IDENTITY = new Quaternionf();

    @Nonnull
    public static final BuilderCodec<PersistentBodyRuntimeStateDto> CODEC =
        BuilderCodec.builder(PersistentBodyRuntimeStateDto.class, PersistentBodyRuntimeStateDto::new)
            .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.position.set(value != null ? value : ZERO),
                PersistentBodyRuntimeStateDto::getPosition)
            .add()
            .append(new KeyedCodec<>("Rotation", ImpulseCodecs.QUATERNIONF, false),
                (dto, value) -> dto.rotation.set(value != null ? value : IDENTITY),
                PersistentBodyRuntimeStateDto::getRotation)
            .add()
            .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.linearVelocity.set(value != null ? value : ZERO),
                PersistentBodyRuntimeStateDto::getLinearVelocity)
            .add()
            .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.angularVelocity.set(value != null ? value : ZERO),
                PersistentBodyRuntimeStateDto::getAngularVelocity)
            .add()
            .append(new KeyedCodec<>("Sleeping", Codec.BOOLEAN, false),
                (dto, value) -> dto.sleeping = value != null && value,
                PersistentBodyRuntimeStateDto::isSleeping)
            .add()
            .build();

    @Nonnull
    private final Vector3f position = new Vector3f();
    @Nonnull
    private final Quaternionf rotation = new Quaternionf();
    @Nonnull
    private final Vector3f linearVelocity = new Vector3f();
    @Nonnull
    private final Vector3f angularVelocity = new Vector3f();
    private boolean sleeping;

    public PersistentBodyRuntimeStateDto() {
    }

    public PersistentBodyRuntimeStateDto(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean sleeping) {
        this.position.set(Objects.requireNonNull(position, "position"));
        this.rotation.set(Objects.requireNonNull(rotation, "rotation"));
        this.linearVelocity.set(Objects.requireNonNull(linearVelocity, "linearVelocity"));
        this.angularVelocity.set(Objects.requireNonNull(angularVelocity, "angularVelocity"));
        this.sleeping = sleeping;
    }

    @Nonnull
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    @Nonnull
    public Quaternionf getRotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return new Vector3f(linearVelocity);
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return new Vector3f(angularVelocity);
    }

    public boolean isSleeping() {
        return sleeping;
    }

    @Nonnull
    public PersistentBodyRuntimeStateDto copy() {
        return new PersistentBodyRuntimeStateDto(position,
            rotation,
            linearVelocity,
            angularVelocity,
            sleeping);
    }
}
