package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PhysicsStorePersistenceValidation {

    private PhysicsStorePersistenceValidation() {
    }

    @Nonnull
    static Validator<Float> finiteFloat(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Float value, ValidationResults results) {
                if (value != null && !Float.isFinite(value)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<Float> nonNegativeFiniteFloat(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Float value, ValidationResults results) {
                if (value != null && (!Float.isFinite(value) || value < 0.0f)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<Vector3f> finiteVector(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Vector3f value, ValidationResults results) {
                if (value != null && !isFinite(value)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    static boolean isFinite(@Nonnull Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    static boolean isFinite(@Nonnull Quaternionf value) {
        return Float.isFinite(value.x)
            && Float.isFinite(value.y)
            && Float.isFinite(value.z)
            && Float.isFinite(value.w)
            && value.lengthSquared() > 0.0f;
    }
}
