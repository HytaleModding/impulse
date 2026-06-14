package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * One-tick ordered body commands drained by PhysicsStore systems.
 */
public final class BodyCommandComponent implements Component<PhysicsStore> {

    private static final Entry[] EMPTY_ENTRIES = new Entry[0];

    @Nonnull
    public static final BuilderCodec<BodyCommandComponent> CODEC = BuilderCodec.builder(
            BodyCommandComponent.class,
            BodyCommandComponent::new)
        .append(new KeyedCodec<>("Commands",
                new ArrayCodec<>(Entry.CODEC, Entry[]::new),
                false),
            (component, value) -> component.entries = copyEntries(value),
            BodyCommandComponent::entries)
        .add()
        .build();

    @Nonnull
    private Entry[] entries = EMPTY_ENTRIES;

    public BodyCommandComponent() {
    }

    private BodyCommandComponent(@Nonnull Entry[] entries) {
        this.entries = copyEntries(entries);
    }

    @Nonnull
    public static BodyCommandComponent wake() {
        return new BodyCommandComponent(new Entry[] {Entry.wake()});
    }

    @Nonnull
    public static BodyCommandComponent sleep() {
        return new BodyCommandComponent(new Entry[] {Entry.sleep()});
    }

    @Nonnull
    public static BodyCommandComponent setType(@Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        return new BodyCommandComponent(new Entry[] {Entry.setType(bodyType, activate)});
    }

    @Nonnull
    public static BodyCommandComponent setCollisionFilter(int collisionGroup,
        int collisionMask,
        boolean activate) {
        return new BodyCommandComponent(new Entry[] {
            Entry.setCollisionFilter(collisionGroup, collisionMask, activate)
        });
    }

    @Nonnull
    public static BodyCommandComponent vector(@Nonnull Kind kind,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ) {
        return new BodyCommandComponent(new Entry[] {
            Entry.vector(kind, x, y, z, hasOffset, offsetX, offsetY, offsetZ)
        });
    }

    @Nonnull
    public BodyCommandComponent append(@Nonnull BodyCommandComponent other) {
        Entry[] otherEntries = other.entries();
        if (otherEntries.length == 0) {
            return clone();
        }
        Entry[] merged = Arrays.copyOf(entries, entries.length + otherEntries.length);
        for (int index = 0; index < otherEntries.length; index++) {
            merged[entries.length + index] = otherEntries[index].clone();
        }
        return new BodyCommandComponent(merged);
    }

    @Nonnull
    public Entry[] entries() {
        return copyEntries(entries);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyCommandComponent> getComponentType() {
        return PhysicsStoreTypes.bodyCommandComponentType();
    }

    @Nonnull
    @Override
    public BodyCommandComponent clone() {
        return new BodyCommandComponent(entries);
    }

    @Nonnull
    private static Entry[] copyEntries(Entry[] entries) {
        if (entries == null || entries.length == 0) {
            return EMPTY_ENTRIES;
        }
        return Arrays.stream(entries)
            .filter(Objects::nonNull)
            .map(Entry::clone)
            .toArray(Entry[]::new);
    }

    /**
     * One ordered body command entry.
     */
    public static final class Entry {

        @Nonnull
        public static final BuilderCodec<Entry> CODEC = BuilderCodec.builder(Entry.class,
                Entry::new)
            .append(new KeyedCodec<>("Kind", new EnumCodec<>(Kind.class), false),
                (entry, value) -> entry.kind = value != null ? value : Kind.WAKE,
                Entry::getKind)
            .add()
            .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
                (entry, value) -> entry.bodyType = value != null
                    ? value
                    : PhysicsBodyType.DYNAMIC,
                Entry::getBodyType)
            .add()
            .append(new KeyedCodec<>("Activate", Codec.BOOLEAN, false),
                (entry, value) -> entry.activate = value != null && value,
                Entry::isActivate)
            .add()
            .append(new KeyedCodec<>("X", Codec.FLOAT, false),
                (entry, value) -> entry.x = value != null ? value : 0.0f,
                Entry::getX)
            .add()
            .append(new KeyedCodec<>("Y", Codec.FLOAT, false),
                (entry, value) -> entry.y = value != null ? value : 0.0f,
                Entry::getY)
            .add()
            .append(new KeyedCodec<>("Z", Codec.FLOAT, false),
                (entry, value) -> entry.z = value != null ? value : 0.0f,
                Entry::getZ)
            .add()
            .append(new KeyedCodec<>("HasOffset", Codec.BOOLEAN, false),
                (entry, value) -> entry.hasOffset = value != null && value,
                Entry::hasOffset)
            .add()
            .append(new KeyedCodec<>("OffsetX", Codec.FLOAT, false),
                (entry, value) -> entry.offsetX = value != null ? value : 0.0f,
                Entry::getOffsetX)
            .add()
            .append(new KeyedCodec<>("OffsetY", Codec.FLOAT, false),
                (entry, value) -> entry.offsetY = value != null ? value : 0.0f,
                Entry::getOffsetY)
            .add()
            .append(new KeyedCodec<>("OffsetZ", Codec.FLOAT, false),
                (entry, value) -> entry.offsetZ = value != null ? value : 0.0f,
                Entry::getOffsetZ)
            .add()
            .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
                (entry, value) -> entry.collisionGroup = value != null
                    ? value
                    : PhysicsCollisionFilters.DYNAMIC_BODY,
                Entry::getCollisionGroup)
            .add()
            .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
                (entry, value) -> entry.collisionMask = value != null
                    ? value
                    : PhysicsCollisionFilters.ALL,
                Entry::getCollisionMask)
            .add()
            .build();

        @Nonnull
        private Kind kind = Kind.WAKE;
        @Nonnull
        private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
        private boolean activate;
        private float x;
        private float y;
        private float z;
        private boolean hasOffset;
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private int collisionGroup = PhysicsCollisionFilters.DYNAMIC_BODY;
        private int collisionMask = PhysicsCollisionFilters.ALL;

        public Entry() {
        }

        private Entry(@Nonnull Kind kind,
            @Nonnull PhysicsBodyType bodyType,
            boolean activate,
            float x,
            float y,
            float z,
            boolean hasOffset,
            float offsetX,
            float offsetY,
            float offsetZ,
            int collisionGroup,
            int collisionMask) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
            this.activate = activate;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hasOffset = hasOffset;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.collisionGroup = collisionGroup;
            this.collisionMask = collisionMask;
        }

        @Nonnull
        private static Entry wake() {
            return new Entry(Kind.WAKE,
                PhysicsBodyType.DYNAMIC,
                true,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL);
        }

        @Nonnull
        private static Entry sleep() {
            return new Entry(Kind.SLEEP,
                PhysicsBodyType.DYNAMIC,
                false,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL);
        }

        @Nonnull
        private static Entry setType(@Nonnull PhysicsBodyType bodyType,
            boolean activate) {
            return new Entry(Kind.SET_TYPE,
                bodyType,
                activate,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL);
        }

        @Nonnull
        private static Entry setCollisionFilter(int collisionGroup,
            int collisionMask,
            boolean activate) {
            return new Entry(Kind.SET_COLLISION_FILTER,
                PhysicsBodyType.DYNAMIC,
                activate,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f,
                collisionGroup,
                collisionMask);
        }

        @Nonnull
        private static Entry vector(@Nonnull Kind kind,
            float x,
            float y,
            float z,
            boolean hasOffset,
            float offsetX,
            float offsetY,
            float offsetZ) {
            if (!kind.isVector()) {
                throw new IllegalArgumentException("Body command kind is not vector-valued: " + kind);
            }
            return new Entry(kind,
                PhysicsBodyType.DYNAMIC,
                true,
                x,
                y,
                z,
                hasOffset,
                offsetX,
                offsetY,
                offsetZ,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL);
        }

        @Nonnull
        public Kind getKind() {
            return kind;
        }

        @Nonnull
        public PhysicsBodyType getBodyType() {
            return bodyType;
        }

        public boolean isActivate() {
            return activate;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public float getZ() {
            return z;
        }

        public boolean hasOffset() {
            return hasOffset;
        }

        public float getOffsetX() {
            return offsetX;
        }

        public float getOffsetY() {
            return offsetY;
        }

        public float getOffsetZ() {
            return offsetZ;
        }

        public int getCollisionGroup() {
            return collisionGroup;
        }

        public int getCollisionMask() {
            return collisionMask;
        }

        @Nonnull
        @Override
        public Entry clone() {
            return new Entry(kind,
                bodyType,
                activate,
                x,
                y,
                z,
                hasOffset,
                offsetX,
                offsetY,
                offsetZ,
                collisionGroup,
                collisionMask);
        }
    }

    public enum Kind {
        WAKE,
        SLEEP,
        IMPULSE,
        TORQUE_IMPULSE,
        FORCE,
        TORQUE,
        SET_TYPE,
        SET_COLLISION_FILTER;

        public boolean isVector() {
            return this == IMPULSE || this == TORQUE_IMPULSE || this == FORCE || this == TORQUE;
        }
    }
}
