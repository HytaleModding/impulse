package dev.hytalemodding.impulse.examples.commands.persistence;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Top-level manual snapshot file format for the example persistence commands.
 *
 * <p>Contains a {@link PersistentPhysicsWorldResource} (spaces, joints, settings)
 * and an array of {@link PersistentPhysicsSnapshotBody} entries (per-entity body
 * state keyed by UUID). Serialized as BSON/JSON via the Hytale codec system and
 * stored under the examples plugin's data directory.</p>
 */
public class PersistentPhysicsSnapshotFile {

    private static final PersistentPhysicsSnapshotBody[] EMPTY_BODIES =
        new PersistentPhysicsSnapshotBody[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsSnapshotFile> CODEC = BuilderCodec.builder(
            PersistentPhysicsSnapshotFile.class,
            PersistentPhysicsSnapshotFile::new)
        .append(new KeyedCodec<>("World", PersistentPhysicsWorldResource.CODEC),
            (file, value) -> file.world = value.clone(),
            file -> file.world)
        .add()
        .append(new KeyedCodec<>("Bodies",
                new ArrayCodec<>(PersistentPhysicsSnapshotBody.CODEC, PersistentPhysicsSnapshotBody[]::new)),
            (file, value) -> file.bodies = copyBodies(value),
            PersistentPhysicsSnapshotFile::getBodies)
        .add()
        .build();

    @Nonnull
    private PersistentPhysicsWorldResource world = new PersistentPhysicsWorldResource();
    @Nonnull
    private PersistentPhysicsSnapshotBody[] bodies = EMPTY_BODIES;

    public PersistentPhysicsSnapshotFile() {
    }

    public PersistentPhysicsSnapshotFile(@Nonnull PersistentPhysicsWorldResource world,
        @Nonnull PersistentPhysicsSnapshotBody[] bodies) {
        this.world = world;
        this.bodies = bodies;
    }

    @Nonnull
    public PersistentPhysicsWorldResource getWorld() {
        return world.clone();
    }

    public void setWorld(@Nonnull PersistentPhysicsWorldResource world) {
        this.world = world.clone();
    }

    @Nonnull
    public PersistentPhysicsSnapshotBody[] getBodies() {
        return copyBodies(bodies);
    }

    public void setBodies(@Nonnull PersistentPhysicsSnapshotBody[] bodies) {
        this.bodies = copyBodies(bodies);
    }

    @Nonnull
    private static PersistentPhysicsSnapshotBody[] copyBodies(
        @Nonnull PersistentPhysicsSnapshotBody[] source) {
        PersistentPhysicsSnapshotBody[] copy = new PersistentPhysicsSnapshotBody[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].copy();
        }
        return copy;
    }
}
