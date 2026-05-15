package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

public class ImpulseControllableComponent implements Component<EntityStore> {

    public static ComponentType<EntityStore, ImpulseControllableComponent> getComponentType() {
        return ImpulsePlugin.get().getImpulseControllableComponentType();
    }

    @Nonnull
    @Override
    public ImpulseControllableComponent clone() {
        return new ImpulseControllableComponent();
    }
}
