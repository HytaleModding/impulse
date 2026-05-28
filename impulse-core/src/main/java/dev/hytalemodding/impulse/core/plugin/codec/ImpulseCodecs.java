package dev.hytalemodding.impulse.core.plugin.codec;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;

/**
 * Shared plugin-facing codecs for value types used by Impulse components.
 */
public final class ImpulseCodecs {

    @Nonnull
    public static final BuilderCodec<Quaternionf> QUATERNIONF = BuilderCodec.builder(
            Quaternionf.class,
            Quaternionf::new)
        .append(new KeyedCodec<>("X", Codec.FLOAT), (value, x) -> value.x = x, value -> value.x)
        .addValidator(finiteFloat("Persisted quaternion X must be finite"))
        .add()
        .append(new KeyedCodec<>("Y", Codec.FLOAT), (value, y) -> value.y = y, value -> value.y)
        .addValidator(finiteFloat("Persisted quaternion Y must be finite"))
        .add()
        .append(new KeyedCodec<>("Z", Codec.FLOAT), (value, z) -> value.z = z, value -> value.z)
        .addValidator(finiteFloat("Persisted quaternion Z must be finite"))
        .add()
        .append(new KeyedCodec<>("W", Codec.FLOAT), (value, w) -> value.w = w, value -> value.w)
        .addValidator(finiteFloat("Persisted quaternion W must be finite"))
        .add()
        .afterDecode(ImpulseCodecs::validateQuaternionAfterDecode)
        .build();

    private ImpulseCodecs() {
    }

    public static boolean isFiniteAndNonZero(@Nullable Quaternionf quaternion) {
        if (quaternion == null) {
            return false;
        }
        float lengthSquared = quaternion.x * quaternion.x
            + quaternion.y * quaternion.y
            + quaternion.z * quaternion.z
            + quaternion.w * quaternion.w;
        return Float.isFinite(lengthSquared) && lengthSquared > 0.0f;
    }

    @Nonnull
    private static Validator<Float> finiteFloat(@Nonnull String message) {
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

    private static void validateQuaternionAfterDecode(@Nonnull Quaternionf quaternion,
        @Nonnull ExtraInfo extraInfo) {
        ValidationResults results = extraInfo.getValidationResults();
        if (!isFiniteAndNonZero(quaternion)) {
            results.fail("Persisted quaternion must be finite and non-zero");
        }
        results._processValidationResults();
    }
}
