package dev.hytalemodding.impulse.core.internal.crucible;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class CrucibleBackends {

    private static final String BACKEND_PROPERTY = "impulse.crucible.backend";
    private static final BackendId RAPIER_BACKEND_ID = new BackendId("impulse:rapier");

    private CrucibleBackends() {
    }

    @Nonnull
    static BackendId requireBackendId() {
        return selectBackendId(Impulse.getRuntimeProviders(), System.getProperty(BACKEND_PROPERTY));
    }

    @Nonnull
    static BackendId selectBackendId(@Nonnull Collection<PhysicsBackendRuntimeProvider> providers,
        @Nullable String configuredBackendId) {
        List<BackendId> backendIds = new ArrayList<>();
        for (PhysicsBackendRuntimeProvider provider : providers) {
            backendIds.add(provider.getId());
        }
        backendIds.sort(Comparator.comparing(BackendId::value));

        if (backendIds.isEmpty()) {
            throw new IllegalStateException("No physics backend runtimes registered");
        }

        String configured = configuredBackendId == null ? "" : configuredBackendId.trim();
        if (!configured.isEmpty()) {
            BackendId requestedBackendId = new BackendId(configured);
            if (backendIds.contains(requestedBackendId)) {
                return requestedBackendId;
            }
            throw new IllegalStateException("Configured Crucible physics backend "
                + requestedBackendId + " is not registered. Available backend runtimes: "
                + formatBackendIds(backendIds));
        }

        if (backendIds.contains(RAPIER_BACKEND_ID)) {
            return RAPIER_BACKEND_ID;
        }

        if (backendIds.size() == 1) {
            return backendIds.getFirst();
        }

        throw new IllegalStateException("Multiple physics backends are registered and Rapier is "
            + "not available. Set -D" + BACKEND_PROPERTY + "=<id>. Available backends: "
            + formatBackendIds(backendIds));
    }

    @Nonnull
    private static String formatBackendIds(@Nonnull List<BackendId> backendIds) {
        List<String> values = new ArrayList<>();
        for (BackendId backendId : backendIds) {
            values.add(backendId.value());
        }
        return String.join(", ", values);
    }
}
