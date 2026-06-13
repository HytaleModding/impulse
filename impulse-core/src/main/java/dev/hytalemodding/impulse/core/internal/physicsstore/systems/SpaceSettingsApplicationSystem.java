package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettingValue;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.api.BackendId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies changed backend-facing space settings after space binding and before body binding.
 */
public final class SpaceSettingsApplicationSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Set<UUID> pending = runtime.drainPendingSpaceSettings();
        if (pending.isEmpty()) {
            return;
        }
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        for (UUID spaceUuid : pending) {
            Ref<PhysicsStore> ref = identity.getByUuid(spaceUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            applyIfBound(store, runtime, ref, spaceUuid);
        }
    }

    static boolean applyIfBound(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID spaceUuid) {
        BackendSpaceHandle handle = runtime.getSpaceHandle(spaceUuid);
        if (handle == null) {
            return false;
        }
        PhysicsBackendRuntime backendRuntime = backendRuntime(runtime, spaceUuid);
        if (backendRuntime == null) {
            return false;
        }
        SolverSettingsComponent solverSettings = store.getComponent(ref,
            SolverSettingsComponent.getComponentType());
        ExtensionSettingsComponent extensionSettings = store.getComponent(ref,
            ExtensionSettingsComponent.getComponentType());
        applyBackendSettings(backendRuntime,
            handle,
            solverSettings != null ? solverSettings : new SolverSettingsComponent(),
            extensionSettings);
        return true;
    }

    static void applyBackendSettings(@Nonnull PhysicsBackendRuntime runtime,
        @Nonnull BackendSpaceHandle handle,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nullable ExtensionSettingsComponent extensionSettings) {
        if (runtime.supportsSolverTuning(handle.value())) {
            runtime.applySolverTuning(handle.value(),
                new PhysicsSolverTuning(solverSettings.getSolverIterations(),
                    solverSettings.getStabilizationIterations()));
        }
        if (runtime.supportsActivationTuning(handle.value())) {
            runtime.applyActivationTuning(handle.value(),
                new PhysicsActivationTuning(solverSettings.getDynamicSleepLinearThreshold(),
                    solverSettings.getDynamicSleepAngularThreshold(),
                    solverSettings.getDynamicSleepTimeUntilSleep()));
        }
        if (extensionSettings == null) {
            return;
        }
        Map<PhysicsBackendExtensionId, Map<String, PhysicsExtensionSettingValue>> settingsByExtension =
            extensionSettings.asMap();
        for (PhysicsBackendExtensionId extensionId : settingsByExtension.keySet()) {
            Map<String, PhysicsExtensionSettingValue> settings = settingsByExtension.get(extensionId);
            runtime.applyExtensionSettings(handle.value(),
                new PhysicsCapabilityId(extensionId.value()),
                consumer -> settings.forEach((key, value) -> consumer.accept(key, value.value())));
        }
    }

    @Nullable
    private static PhysicsBackendRuntime backendRuntime(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID spaceUuid) {
        BackendId backendId = runtime.getSpaceBackendId(spaceUuid);
        if (backendId == null) {
            return null;
        }
        return runtime.getRuntime(backendId);
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
