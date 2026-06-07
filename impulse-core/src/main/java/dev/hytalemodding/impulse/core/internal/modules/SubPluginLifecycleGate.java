package dev.hytalemodding.impulse.core.internal.modules;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * Shared runtime gate for optional Impulse subplugins.
 */
public final class SubPluginLifecycleGate {

    private final String disabledMessage;
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong(1L);
    private final CopyOnWriteArrayList<Runnable> disableCallbacks = new CopyOnWriteArrayList<>();

    public SubPluginLifecycleGate(@Nonnull String disabledMessage) {
        this.disabledMessage = Objects.requireNonNull(disabledMessage, "disabledMessage");
    }

    public void enable() {
        if (enabled.compareAndSet(false, true)) {
            generation.incrementAndGet();
        }
    }

    public void disable() {
        if (!enabled.compareAndSet(true, false)) {
            return;
        }
        generation.incrementAndGet();
        for (Runnable callback : disableCallbacks) {
            callback.run();
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public long generation() {
        return generation.get();
    }

    public void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException(disabledMessage);
        }
    }

    public void onDisable(@Nonnull Runnable callback) {
        disableCallbacks.add(Objects.requireNonNull(callback, "callback"));
    }
}
