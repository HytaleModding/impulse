package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Capability-keyed extension settings forwarded to compatible backends.
 */
public class PhysicsExtensionSettings {

    @Nonnull
    private final Map<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> values =
        new LinkedHashMap<>();

    public PhysicsExtensionSettings() {
    }

    public PhysicsExtensionSettings(@Nonnull PhysicsExtensionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        for (Map.Entry<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> entry
            : settings.values.entrySet()) {
            values.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
    }

    public void setString(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        @Nonnull String value) {
        set(extensionId, key, PhysicsExtensionSettingValue.ofString(value));
    }

    public void setInt(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        int value) {
        set(extensionId, key, PhysicsExtensionSettingValue.ofInt(value));
    }

    public void setFloat(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        float value) {
        set(extensionId, key, PhysicsExtensionSettingValue.ofFloat(value));
    }

    public void setBoolean(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        boolean value) {
        set(extensionId, key, PhysicsExtensionSettingValue.ofBoolean(value));
    }

    public void set(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key,
        @Nonnull PhysicsExtensionSettingValue value) {
        Objects.requireNonNull(extensionId, "extensionId");
        requireKey(key);
        Objects.requireNonNull(value, "value");
        values.computeIfAbsent(extensionId, _ -> new LinkedHashMap<>()).put(key, value);
    }

    @Nonnull
    public Optional<PhysicsExtensionSettingValue> get(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key) {
        Objects.requireNonNull(extensionId, "extensionId");
        requireKey(key);
        Map<String, PhysicsExtensionSettingValue> group = values.get(extensionId);
        if (group == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(group.get(key));
    }

    @Nonnull
    public Optional<String> getString(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key) {
        return get(extensionId, key)
            .filter(value -> value.kind() == PhysicsExtensionSettingValue.Kind.STRING)
            .map(PhysicsExtensionSettingValue::value);
    }

    @Nonnull
    public Optional<Integer> getInt(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key) {
        return get(extensionId, key)
            .filter(value -> value.kind() == PhysicsExtensionSettingValue.Kind.INTEGER)
            .map(value -> Integer.parseInt(value.value()));
    }

    @Nonnull
    public Optional<Float> getFloat(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key) {
        return get(extensionId, key)
            .filter(value -> value.kind() == PhysicsExtensionSettingValue.Kind.FLOAT)
            .map(value -> Float.parseFloat(value.value()));
    }

    @Nonnull
    public Optional<Boolean> getBoolean(@Nonnull PhysicsBackendExtensionId extensionId,
        @Nonnull String key) {
        return get(extensionId, key)
            .filter(value -> value.kind() == PhysicsExtensionSettingValue.Kind.BOOLEAN)
            .map(value -> Boolean.parseBoolean(value.value()));
    }

    @Nonnull
    public Map<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> asMap() {
        Map<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> copy =
            new LinkedHashMap<>();
        for (Map.Entry<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> entry
            : values.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    @Nonnull
    public Map<String, String> asStringMap(@Nonnull PhysicsBackendExtensionId extensionId) {
        Objects.requireNonNull(extensionId, "extensionId");
        Map<String, PhysicsExtensionSettingValue> group = values.get(extensionId);
        if (group == null) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, PhysicsExtensionSettingValue> entry : group.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().value());
        }
        return copy;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    private static void requireKey(@Nonnull String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Extension setting key cannot be blank");
        }
    }
}
