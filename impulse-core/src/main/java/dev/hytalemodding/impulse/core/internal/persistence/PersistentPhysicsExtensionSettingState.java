package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettingValue;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted capability-keyed extension setting.
 */
@Getter
final class PersistentPhysicsExtensionSettingState {

    @Nonnull
    static final BuilderCodec<PersistentPhysicsExtensionSettingState> CODEC =
        BuilderCodec.builder(PersistentPhysicsExtensionSettingState.class,
                PersistentPhysicsExtensionSettingState::new)
            .append(new KeyedCodec<>("CapabilityId", Codec.STRING),
                (state, value) -> state.capabilityId = value,
                PersistentPhysicsExtensionSettingState::getCapabilityId)
            .addValidator(Validators.nonNull())
            .addValidator(PersistentPhysicsValidation.nonBlankString(
                "Persisted extension capability id cannot be blank"))
            .add()
            .append(new KeyedCodec<>("Key", Codec.STRING),
                (state, value) -> state.key = value,
                PersistentPhysicsExtensionSettingState::getKey)
            .addValidator(Validators.nonNull())
            .addValidator(PersistentPhysicsValidation.nonBlankString(
                "Persisted extension setting key cannot be blank"))
            .add()
            .append(new KeyedCodec<>("Kind", new EnumCodec<>(PhysicsExtensionSettingValue.Kind.class)),
                (state, value) -> state.kind = value,
                PersistentPhysicsExtensionSettingState::getKind)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Value", Codec.STRING),
                (state, value) -> state.value = value,
                PersistentPhysicsExtensionSettingState::getValue)
            .addValidator(Validators.nonNull())
            .addValidator(PersistentPhysicsValidation.nonBlankString(
                "Persisted extension setting value cannot be blank"))
            .add()
            .build();

    @Nonnull
    @Setter
    private String capabilityId = "";
    @Nonnull
    @Setter
    private String key = "";
    @Nonnull
    @Setter
    private PhysicsExtensionSettingValue.Kind kind = PhysicsExtensionSettingValue.Kind.STRING;
    @Nonnull
    @Setter
    private String value = "";

    @Nonnull
    static PersistentPhysicsExtensionSettingState from(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        @Nonnull PhysicsExtensionSettingValue value) {
        PersistentPhysicsExtensionSettingState state = new PersistentPhysicsExtensionSettingState();
        state.capabilityId = extensionId.value();
        state.key = key;
        state.kind = value.kind();
        state.value = value.value();
        return state;
    }

    void applyTo(@Nonnull PhysicsExtensionSettings settings) {
        settings.set(new PhysicsBackendExtensionId(capabilityId),
            key,
            new PhysicsExtensionSettingValue(kind, value));
    }

    @Nonnull
    PersistentPhysicsExtensionSettingState copy() {
        PersistentPhysicsExtensionSettingState copy = new PersistentPhysicsExtensionSettingState();
        copy.capabilityId = capabilityId;
        copy.key = key;
        copy.kind = kind;
        copy.value = value;
        return copy;
    }
}
