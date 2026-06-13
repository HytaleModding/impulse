package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettingValue;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored backend extension settings for one PhysicsStore space row.
 */
public final class ExtensionSettingsComponent implements Component<PhysicsStore> {

    private static final Entry[] EMPTY_ENTRIES = new Entry[0];

    @Nonnull
    public static final BuilderCodec<ExtensionSettingsComponent> CODEC = BuilderCodec.builder(
            ExtensionSettingsComponent.class,
            ExtensionSettingsComponent::new)
        .append(new KeyedCodec<>("Settings",
                new ArrayCodec<>(Entry.CODEC, Entry[]::new),
                false),
            (component, value) -> component.entries = copyEntries(value),
            ExtensionSettingsComponent::entries)
        .add()
        .build();

    @Nonnull
    private Entry[] entries = EMPTY_ENTRIES;

    public ExtensionSettingsComponent() {
    }

    public ExtensionSettingsComponent(@Nonnull PhysicsExtensionSettings settings) {
        entries = entriesFrom(settings);
    }

    private ExtensionSettingsComponent(@Nonnull Entry[] entries) {
        this.entries = copyEntries(entries);
    }

    @Nonnull
    public Entry[] entries() {
        return copyEntries(entries);
    }

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        copyTo(settings.getExtensionSettings());
    }

    public void copyTo(@Nonnull PhysicsExtensionSettings settings) {
        for (Entry entry : entries) {
            entry.copyTo(settings);
        }
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ExtensionSettingsComponent> getComponentType() {
        return PhysicsStoreTypes.extensionSettingsComponentType();
    }

    @Nonnull
    @Override
    public ExtensionSettingsComponent clone() {
        return new ExtensionSettingsComponent(entries);
    }

    @Nonnull
    private static Entry[] entriesFrom(@Nonnull PhysicsExtensionSettings settings) {
        return settings.asMap().entrySet().stream()
            .flatMap(entry -> entry.getValue().entrySet().stream()
                .map(setting -> new Entry(entry.getKey().value(),
                    setting.getKey(),
                    setting.getValue().kind(),
                    setting.getValue().value())))
            .toArray(Entry[]::new);
    }

    @Nonnull
    private static Entry[] copyEntries(Entry[] entries) {
        if (entries == null || entries.length == 0) {
            return EMPTY_ENTRIES;
        }
        return Arrays.stream(entries)
            .map(Entry::clone)
            .toArray(Entry[]::new);
    }

    @Nonnull
    public Map<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> asMap() {
        PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
        copyTo(settings);
        return settings.asMap();
    }

    /**
     * One capability-keyed extension setting.
     */
    public static final class Entry {

        @Nonnull
        public static final BuilderCodec<Entry> CODEC = BuilderCodec.builder(Entry.class,
                Entry::new)
            .append(new KeyedCodec<>("CapabilityId", Codec.STRING),
                (entry, value) -> entry.capabilityId = value,
                Entry::capabilityId)
            .add()
            .append(new KeyedCodec<>("Key", Codec.STRING),
                (entry, value) -> entry.key = value,
                Entry::key)
            .add()
            .append(new KeyedCodec<>("Kind",
                    new EnumCodec<>(PhysicsExtensionSettingValue.Kind.class)),
                (entry, value) -> entry.kind = value,
                Entry::kind)
            .add()
            .append(new KeyedCodec<>("Value", Codec.STRING),
                (entry, value) -> entry.value = value,
                Entry::value)
            .add()
            .build();

        @Nonnull
        private String capabilityId = "";
        @Nonnull
        private String key = "";
        @Nonnull
        private PhysicsExtensionSettingValue.Kind kind =
            PhysicsExtensionSettingValue.Kind.STRING;
        @Nonnull
        private String value = "";

        public Entry() {
        }

        private Entry(@Nonnull String capabilityId,
            @Nonnull String key,
            @Nonnull PhysicsExtensionSettingValue.Kind kind,
            @Nonnull String value) {
            this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            this.key = Objects.requireNonNull(key, "key");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.value = Objects.requireNonNull(value, "value");
        }

        @Nonnull
        public String capabilityId() {
            return capabilityId;
        }

        @Nonnull
        public String key() {
            return key;
        }

        @Nonnull
        public PhysicsExtensionSettingValue.Kind kind() {
            return kind;
        }

        @Nonnull
        public String value() {
            return value;
        }

        private void copyTo(@Nonnull PhysicsExtensionSettings settings) {
            settings.set(new PhysicsBackendExtensionId(capabilityId),
                key,
                new PhysicsExtensionSettingValue(kind, value));
        }

        @Nonnull
        @Override
        public Entry clone() {
            return new Entry(capabilityId, key, kind, value);
        }
    }
}
