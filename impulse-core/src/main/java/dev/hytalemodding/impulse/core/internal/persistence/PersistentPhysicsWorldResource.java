package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.Validator;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistenceResource;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistenceSyncResult;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Arrays;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Codec-backed world-level physics resource for the persistence layer.
 *
 * <p>This resource stores the world-level state that does not belong
 * on individual entities: the space definitions (id, backend, gravity, world-collision
 * settings), the body states (keyed by stable physics body keys), the joint
 * definitions (keyed by endpoint body keys), and world simulation settings.</p>
 *
 * <p>The {@code runtimeRestorePending} flag is set by {@code afterDecode} whenever
 * Hytale deserializes this resource. It signals the hydration systems that they
 * need to recreate live spaces, bodies, and joints from the persisted data. The
 * flag is cleared once restore finishes.</p>
 *
 * <p>Restore semantics are intentionally asymmetric. Missing saved backends are a
 * hard failure because the world-level space definitions cannot be recreated at
 * all. Missing entities, missing runtime bodies, and unresolved joints are softer
 * failures: those entries are skipped, counted, and reported so restore can finish
 * instead of hanging forever.</p>
 */
public class PersistentPhysicsWorldResource extends PhysicsPersistenceResource {

    public static final int CURRENT_SCHEMA_VERSION = PhysicsPersistenceResource.CURRENT_SCHEMA_VERSION;
    private static final PersistentPhysicsSpaceState[] EMPTY_SPACES = new PersistentPhysicsSpaceState[0];
    private static final PersistentPhysicsBodyState[] EMPTY_BODIES = new PersistentPhysicsBodyState[0];
    private static final PersistentPhysicsJointState[] EMPTY_JOINTS = new PersistentPhysicsJointState[0];
    private static final PersistentPhysicsStateBlock[] EMPTY_STATE_BLOCKS = new PersistentPhysicsStateBlock[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsWorldResource> CODEC = BuilderCodec.builder(
            PersistentPhysicsWorldResource.class,
            PersistentPhysicsWorldResource::new)
        .append(new KeyedCodec<>("SchemaVersion", Codec.INTEGER, false),
            PersistentPhysicsWorldResource::setSchemaVersion,
            PersistentPhysicsWorldResource::getSchemaVersion)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(CURRENT_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION))
        .add()
        .append(new KeyedCodec<>("SimulationSteps", Codec.INTEGER),
            (resource, value) -> resource.worldSettings.setSimulationSteps(value),
            resource -> resource.worldSettings.getSimulationSteps())
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(
            PhysicsWorldSettings.MIN_SIMULATION_STEPS,
            PhysicsWorldSettings.MAX_SIMULATION_STEPS))
        .add()
        .append(new KeyedCodec<>("StepMode", Codec.STRING),
            (resource, value) -> resource.worldSettings.setStepMode(PhysicsStepMode.parse(value)),
            resource -> resource.worldSettings.getStepMode().getSerializedName())
        .addValidator(Validators.nonNull())
        .addValidator(stepModeName())
        .add()
        .append(new KeyedCodec<>("StepSchedulingMode", Codec.STRING),
            (resource, value) -> resource.worldSettings.setStepSchedulingMode(
                PhysicsStepSchedulingMode.parse(value)),
            resource -> resource.worldSettings.getStepSchedulingMode().getSerializedName())
        .addValidator(Validators.nonNull())
        .addValidator(stepSchedulingModeName())
        .add()
        .append(new KeyedCodec<>("EventCollectionMode", Codec.STRING, false),
            (resource, value) -> resource.worldSettings.setEventCollectionMode(
                PhysicsEventCollectionMode.parse(value)),
            resource -> resource.worldSettings.getEventCollectionMode().getSerializedName())
        .addValidator(Validators.nonNull())
        .addValidator(eventCollectionModeName())
        .add()
        .append(new KeyedCodec<>("MaxStepDt", Codec.FLOAT),
            (resource, value) -> resource.worldSettings.setMaxStepDt(value),
            resource -> resource.worldSettings.getMaxStepDt())
        .addValidator(Validators.nonNull())
        .addValidator(Validators.greaterThan(0.0f))
        .addValidator(Validators.max(Float.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("Spaces",
                new ArrayCodec<>(PersistentPhysicsSpaceState.CODEC, PersistentPhysicsSpaceState[]::new)),
            (resource, value) -> resource.spaces = copySpaces(value),
            PersistentPhysicsWorldResource::getSpaces)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.nonNullArrayElements())
        .add()
        .append(new KeyedCodec<>("BodyBlocks",
                new ArrayCodec<>(PersistentPhysicsStateBlock.CODEC, PersistentPhysicsStateBlock[]::new)),
            (resource, value) -> resource.bodyBlocks = copyStateBlocks(value),
            PersistentPhysicsWorldResource::getBodyBlocksForCodec)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.nonNullArrayElements())
        .add()
        .append(new KeyedCodec<>("JointBlocks",
                new ArrayCodec<>(PersistentPhysicsStateBlock.CODEC, PersistentPhysicsStateBlock[]::new)),
            (resource, value) -> resource.jointBlocks = copyStateBlocks(value),
            PersistentPhysicsWorldResource::getJointBlocksForCodec)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.nonNullArrayElements())
        .add()
        .afterDecode(PersistentPhysicsWorldResource::afterCodecDecode)
        .build();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    @Nonnull
    private final PhysicsWorldSettings worldSettings = new PhysicsWorldSettings();
    @Nonnull
    private PersistentPhysicsSpaceState[] spaces = EMPTY_SPACES;
    @Nonnull
    private PersistentPhysicsBodyState[] bodies = EMPTY_BODIES;
    @Nonnull
    private PersistentPhysicsJointState[] joints = EMPTY_JOINTS;
    @Nonnull
    private PersistentPhysicsStateBlock[] bodyBlocks = EMPTY_STATE_BLOCKS;
    @Nonnull
    private PersistentPhysicsStateBlock[] jointBlocks = EMPTY_STATE_BLOCKS;
    private transient boolean runtimeRestorePending;
    private transient boolean runtimeSpaceBootstrapComplete;
    private transient boolean runtimeRestoreFailed;
    @Nonnull
    private transient String runtimeRestoreFailureMessage = "";
    private transient int runtimeRestoredSpaceCount;
    private transient int runtimeRestoredBodyCount;
    private transient int runtimeRestoredJointCount;
    private transient long runtimeRestoreGeneration;
    @Nonnull
    private final transient Object2IntMap<String> runtimeSkippedBodiesByReason = new Object2IntLinkedOpenHashMap<>();
    @Nonnull
    private final transient Set<RigidBodyKey> runtimeSkippedBodyKeys = new ObjectOpenHashSet<>();
    @Nonnull
    private final transient Object2IntMap<String> runtimeSkippedJointsByReason = new Object2IntLinkedOpenHashMap<>();
    @Nonnull
    private final transient Set<String> runtimeSkippedJointKeys = new ObjectOpenHashSet<>();
    private transient boolean runtimeSnapshotSynced;
    private transient int runtimeSnapshotSyncSkipTicks;

    public PersistentPhysicsWorldResource() {
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static ResourceType<EntityStore, PersistentPhysicsWorldResource> getResourceType() {
        return (ResourceType<EntityStore, PersistentPhysicsWorldResource>)
            ImpulsePlugin.get().getPersistentPhysicsWorldResourceType();
    }

    @Override
    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Schema version must be "
                + CURRENT_SCHEMA_VERSION);
        }
        this.schemaVersion = schemaVersion;
    }

    @Nonnull
    @Override
    public PhysicsPersistenceSyncResult saveRuntimeSnapshot(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource runtime) {
        return new PhysicsPersistenceSyncResult(false,
            getSpaceCount(),
            getBodyCount(),
            getJointCount(),
            "legacy-persistence-import-only");
    }

    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return new PhysicsWorldSettings(worldSettings);
    }

    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        worldSettings.copyFrom(settings);
    }

    @Nonnull
    public PersistentPhysicsSpaceState[] getSpaces() {
        return copySpaces(spaces);
    }

    @Override
    public int getSpaceCount() {
        return spaces.length;
    }

    public void setSpaces(@Nonnull PersistentPhysicsSpaceState[] spaces) {
        this.spaces = copySpaces(spaces);
    }

    @Nonnull
    public PersistentPhysicsBodyState[] getBodies() {
        return copyBodies(bodies);
    }

    @Override
    public int getBodyCount() {
        return bodies.length;
    }

    public void setBodies(@Nonnull PersistentPhysicsBodyState[] bodies) {
        this.bodies = copyBodies(bodies);
    }

    @Nonnull
    public PersistentPhysicsJointState[] getJoints() {
        return copyJoints(joints);
    }

    @Override
    public int getJointCount() {
        return joints.length;
    }

    public void setJoints(@Nonnull PersistentPhysicsJointState[] joints) {
        this.joints = copyJoints(joints);
    }

    @Override
    public boolean isRuntimeRestorePending() {
        return runtimeRestorePending;
    }

    @Override
    public void markRuntimeRestorePending() {
        runtimeRestoreGeneration++;
        runtimeRestorePending = true;
        runtimeSpaceBootstrapComplete = false;
        runtimeRestoreFailed = false;
        runtimeRestoreFailureMessage = "";
        runtimeRestoredSpaceCount = 0;
        runtimeRestoredBodyCount = 0;
        runtimeRestoredJointCount = 0;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
        runtimeSkippedBodiesByReason.clear();
        runtimeSkippedBodyKeys.clear();
        runtimeSkippedJointsByReason.clear();
        runtimeSkippedJointKeys.clear();
    }

    @Override
    public boolean isRuntimeSpaceBootstrapComplete() {
        return runtimeSpaceBootstrapComplete;
    }

    public void clearRuntimeRestorePending() {
        runtimeRestoreGeneration++;
        runtimeRestorePending = false;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
    }

    public long runtimeRestoreGeneration() {
        return runtimeRestoreGeneration;
    }

    public boolean shouldSyncRuntimeSnapshot(int intervalTicks) {
        if (!runtimeSnapshotSynced || intervalTicks <= 0) {
            return true;
        }
        if (runtimeSnapshotSyncSkipTicks < intervalTicks) {
            runtimeSnapshotSyncSkipTicks++;
            return false;
        }
        return true;
    }

    public void markRuntimeSnapshotSynced() {
        runtimeSnapshotSynced = true;
        runtimeSnapshotSyncSkipTicks = 0;
    }

    public void markRuntimeSpaceBootstrapComplete(int restoredSpaceCount) {
        runtimeSpaceBootstrapComplete = true;
        runtimeRestoredSpaceCount = restoredSpaceCount;
    }

    @Override
    public boolean hasRuntimeRestoreFailed() {
        return runtimeRestoreFailed;
    }

    public void failRuntimeRestore(@Nonnull String message) {
        runtimeRestorePending = false;
        runtimeRestoreFailed = true;
        runtimeRestoreFailureMessage = message;
    }

    public void recordRuntimeBodyRestored() {
        runtimeRestoredBodyCount++;
    }

    public void recordRuntimeBodySkipped(@Nonnull String reason) {
        runtimeSkippedBodiesByReason.put(reason, runtimeSkippedBodiesByReason.getInt(reason) + 1);
    }

    public void recordRuntimeBodySkipped(@Nonnull RigidBodyKey bodyKey, @Nonnull String reason) {
        runtimeSkippedBodyKeys.add(bodyKey);
        recordRuntimeBodySkipped(reason);
    }

    public boolean isRuntimeBodySkipped(@Nonnull RigidBodyKey bodyKey) {
        return runtimeSkippedBodyKeys.contains(bodyKey);
    }

    public void recordRuntimeJointRestored() {
        runtimeRestoredJointCount++;
    }

    public void recordRuntimeJointSkipped(@Nonnull String key, @Nonnull String reason) {
        if (!runtimeSkippedJointKeys.add(key)) {
            return;
        }
        runtimeSkippedJointsByReason.put(reason, runtimeSkippedJointsByReason.getInt(reason) + 1);
    }

    @Override
    public boolean hasRuntimeRestoreSkips() {
        return !runtimeSkippedBodiesByReason.isEmpty() || !runtimeSkippedJointsByReason.isEmpty();
    }

    @Nonnull
    @Override
    public String runtimeRestoreSummary() {
        return "Impulse persistence restore completed: "
            + runtimeRestoredSpaceCount + " spaces, "
            + runtimeRestoredBodyCount + " bodies restored, "
            + countReasons(runtimeSkippedBodiesByReason) + " bodies skipped ("
            + formatReasons(runtimeSkippedBodiesByReason) + "), "
            + runtimeRestoredJointCount + " joints restored, "
            + countReasons(runtimeSkippedJointsByReason) + " joints skipped ("
            + formatReasons(runtimeSkippedJointsByReason) + ").";
    }

    @Nonnull
    @Override
    public String runtimeRestoreFailureSummary() {
        return "Impulse persistence restore failed: " + runtimeRestoreFailureMessage + " "
            + "Partial progress before failure: "
            + runtimeRestoredSpaceCount + " spaces, "
            + runtimeRestoredBodyCount + " bodies restored, "
            + countReasons(runtimeSkippedBodiesByReason) + " bodies skipped, "
            + runtimeRestoredJointCount + " joints restored, "
            + countReasons(runtimeSkippedJointsByReason) + " joints skipped.";
    }

    public void copyFrom(@Nonnull PersistentPhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        setSchemaVersion(other.schemaVersion);
        worldSettings.copyFrom(other.worldSettings);
        spaces = copySpaces(other.spaces);
        bodies = copyBodies(other.bodies);
        joints = copyJoints(other.joints);
        bodyBlocks = EMPTY_STATE_BLOCKS;
        jointBlocks = EMPTY_STATE_BLOCKS;
        runtimeRestorePending = false;
        runtimeSpaceBootstrapComplete = false;
        runtimeRestoreFailed = false;
        runtimeRestoreFailureMessage = "";
        runtimeRestoredSpaceCount = 0;
        runtimeRestoredBodyCount = 0;
        runtimeRestoredJointCount = 0;
        runtimeRestoreGeneration = 0L;
        runtimeSnapshotSynced = false;
        runtimeSnapshotSyncSkipTicks = 0;
        runtimeSkippedBodiesByReason.clear();
        runtimeSkippedBodyKeys.clear();
        runtimeSkippedJointsByReason.clear();
        runtimeSkippedJointKeys.clear();
    }

    private void afterCodecDecode() {
        if (bodyBlocks.length > 0) {
            bodies = PersistentPhysicsStateBlock.decodeBodyBlocks(bodyBlocks);
        }
        if (jointBlocks.length > 0) {
            joints = PersistentPhysicsStateBlock.decodeJointBlocks(jointBlocks);
        }
        bodyBlocks = EMPTY_STATE_BLOCKS;
        jointBlocks = EMPTY_STATE_BLOCKS;
        markRuntimeRestorePending();
    }

    @Nonnull
    private PersistentPhysicsStateBlock[] getBodyBlocksForCodec() {
        if (bodies.length == 0) {
            return EMPTY_STATE_BLOCKS;
        }
        return PersistentPhysicsStateBlock.bodyBlocks(bodies);
    }

    @Nonnull
    private PersistentPhysicsStateBlock[] getJointBlocksForCodec() {
        if (joints.length == 0) {
            return EMPTY_STATE_BLOCKS;
        }
        return PersistentPhysicsStateBlock.jointBlocks(joints);
    }

    @Nonnull
    private static PersistentPhysicsBodyState[] copyBodies(@Nonnull PersistentPhysicsBodyState[] source) {
        PersistentPhysicsBodyState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    @Override
    public PersistentPhysicsWorldResource clone() {
        PersistentPhysicsWorldResource copy = new PersistentPhysicsWorldResource();
        copy.copyFrom(this);
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsSpaceState[] copySpaces(@Nonnull PersistentPhysicsSpaceState[] source) {
        PersistentPhysicsSpaceState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsJointState[] copyJoints(@Nonnull PersistentPhysicsJointState[] source) {
        PersistentPhysicsJointState[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsStateBlock[] copyStateBlocks(@Nullable PersistentPhysicsStateBlock[] source) {
        if (source == null || source.length == 0) {
            return EMPTY_STATE_BLOCKS;
        }
        PersistentPhysicsStateBlock[] copy = Arrays.copyOf(source, source.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i].copy();
        }
        return copy;
    }

    @Nonnull
    private static Validator<String> stepModeName() {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                try {
                    PhysicsStepMode.parse(value);
                } catch (IllegalArgumentException exception) {
                    results.fail("Persistent physics step mode is unknown: " + value);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    private static Validator<String> stepSchedulingModeName() {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                try {
                    PhysicsStepSchedulingMode.parse(value);
                } catch (IllegalArgumentException exception) {
                    results.fail("Persistent physics step scheduling mode is unknown: " + value);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    private static Validator<String> eventCollectionModeName() {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                try {
                    PhysicsEventCollectionMode.parse(value);
                } catch (IllegalArgumentException exception) {
                    results.fail("Persistent physics event collection mode is unknown: " + value);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    private static int countReasons(@Nonnull Object2IntMap<String> reasons) {
        int total = 0;
        for (int count : reasons.values()) {
            total += count;
        }
        return total;
    }

    @Nonnull
    private static String formatReasons(@Nonnull Object2IntMap<String> reasons) {
        if (reasons.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Object2IntMap.Entry<String> entry : reasons.object2IntEntrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getIntValue());
            first = false;
        }
        return builder.toString();
    }
}
