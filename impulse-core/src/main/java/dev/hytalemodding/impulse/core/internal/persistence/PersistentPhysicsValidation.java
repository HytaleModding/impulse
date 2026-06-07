package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.Validator;
import com.hypixel.hytale.codec.validation.ValidationResults;
import dev.hytalemodding.impulse.api.ShapeType;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

final class PersistentPhysicsValidation {

    private PersistentPhysicsValidation() {
    }

    @Nonnull
    static Validator<Float> finiteFloat(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Float value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (!Float.isFinite(value)) {
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
                if (value == null) {
                    return;
                }
                if (!Float.isFinite(value) || value < 0.0f) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<Integer> intAtMost(int max, @Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Integer value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (value > max) {
                    results.fail(message + ": " + value + " > " + max);
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
                if (value == null) {
                    return;
                }
                if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<ShapeType> persistentShapeType() {
        return new Validator<>() {
            @Override
            public void accept(ShapeType value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (value == ShapeType.UNKNOWN || value == ShapeType.VOXELS) {
                    results.fail("Persisted body shape is unsupported: " + value);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<String> nonBlankString(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (value.isBlank()) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<String> stringEquals(@Nonnull String expected, @Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (!expected.equals(value)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<String> stringIn(@Nonnull Set<String> expected, @Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (!expected.contains(value)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    static Validator<byte[]> nonEmptyBytes(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(byte[] value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (value.length == 0) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }
}
