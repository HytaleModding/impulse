package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Validates decoded holder rows before mutating the live PhysicsStore.
 */
final class PhysicsStoreHolderPreflight {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private PhysicsStoreHolderPreflight() {
    }

    @Nonnull
    static Result validate(@Nonnull List<Holder<PhysicsStore>> holders) {
        List<String> errors = new ArrayList<>();
        ObjectOpenHashSet<UUID> seen = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<UUID> spaces = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<UUID> bodies = new ObjectOpenHashSet<>();
        List<BodyRow> bodyRows = new ArrayList<>();
        List<JointRow> jointRows = new ArrayList<>();

        for (Holder<PhysicsStore> holder : holders) {
            UuidComponent uuidComponent = holder.getComponent(UuidComponent.getComponentType());
            if (uuidComponent == null || NIL_UUID.equals(uuidComponent.getUuid())) {
                errors.add("PhysicsStore holder row is missing a durable UUID");
                continue;
            }
            UUID uuid = uuidComponent.getUuid();
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore holder UUID: " + uuid);
            }

            SpaceComponent space = holder.getComponent(SpaceComponent.getComponentType());
            BodyComponent body = holder.getComponent(BodyComponent.getComponentType());
            JointComponent joint = holder.getComponent(JointComponent.getComponentType());
            int primaryComponents = (space != null ? 1 : 0)
                + (body != null ? 1 : 0)
                + (joint != null ? 1 : 0);
            if (primaryComponents == 0) {
                errors.add("PhysicsStore holder " + uuid
                    + " has no space, body, or joint component");
                continue;
            }
            if (primaryComponents > 1) {
                errors.add("PhysicsStore holder " + uuid
                    + " mixes space, body, and joint components");
                continue;
            }
            if (space != null) {
                spaces.add(uuid);
            } else if (body != null) {
                bodies.add(uuid);
                bodyRows.add(new BodyRow(uuid, body.getSpaceUuid()));
            } else {
                jointRows.add(new JointRow(uuid,
                    joint.getSpaceUuid(),
                    joint.getBodyAUuid(),
                    joint.getBodyBUuid()));
            }
        }

        validateBodyRows(bodyRows, spaces, errors);
        validateJointRows(jointRows, spaces, bodies, errors);
        return new Result(errors.isEmpty(), List.copyOf(errors));
    }

    private static void validateBodyRows(@Nonnull List<BodyRow> bodyRows,
        @Nonnull ObjectOpenHashSet<UUID> spaces,
        @Nonnull List<String> errors) {
        for (BodyRow body : bodyRows) {
            if (NIL_UUID.equals(body.spaceUuid()) || !spaces.contains(body.spaceUuid())) {
                errors.add("PhysicsStore holder body " + body.uuid()
                    + " references missing space " + body.spaceUuid());
            }
        }
    }

    private static void validateJointRows(@Nonnull List<JointRow> jointRows,
        @Nonnull ObjectOpenHashSet<UUID> spaces,
        @Nonnull ObjectOpenHashSet<UUID> bodies,
        @Nonnull List<String> errors) {
        for (JointRow joint : jointRows) {
            if (NIL_UUID.equals(joint.spaceUuid()) || !spaces.contains(joint.spaceUuid())) {
                errors.add("PhysicsStore holder joint " + joint.uuid()
                    + " references missing space " + joint.spaceUuid());
            }
            if (NIL_UUID.equals(joint.bodyAUuid()) || !bodies.contains(joint.bodyAUuid())) {
                errors.add("PhysicsStore holder joint " + joint.uuid()
                    + " references missing body A " + joint.bodyAUuid());
            }
            if (NIL_UUID.equals(joint.bodyBUuid()) || !bodies.contains(joint.bodyBUuid())) {
                errors.add("PhysicsStore holder joint " + joint.uuid()
                    + " references missing body B " + joint.bodyBUuid());
            }
        }
    }

    record Result(boolean valid, @Nonnull List<String> errors) {
    }

    private record BodyRow(@Nonnull UUID uuid, @Nonnull UUID spaceUuid) {
    }

    private record JointRow(@Nonnull UUID uuid,
                            @Nonnull UUID spaceUuid,
                            @Nonnull UUID bodyAUuid,
                            @Nonnull UUID bodyBUuid) {
    }
}
