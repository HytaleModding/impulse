package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExplosiveBlockComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<ExplosiveBlockComponent> CODEC = BuilderCodec.builder(
            ExplosiveBlockComponent.class,
            ExplosiveBlockComponent::new)
        .append(new KeyedCodec<>("BlockType", Codec.STRING, false),
            (component, value) -> component.blockType = sanitizeBlockType(value),
            ExplosiveBlockComponent::getBlockType)
        .add()
        .append(new KeyedCodec<>("Generation", Codec.INTEGER, false),
            (component, value) -> component.generation = nonNegative(value, 0),
            ExplosiveBlockComponent::getGeneration)
        .add()
        .append(new KeyedCodec<>("MaxGeneration", Codec.INTEGER, false),
            (component, value) -> component.maxGeneration = nonNegative(value, 0),
            ExplosiveBlockComponent::getMaxGeneration)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
            (component, value) -> component.radius = atLeast(value, 1),
            ExplosiveBlockComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("MaxFragments", Codec.INTEGER, false),
            (component, value) -> component.maxFragments = atLeast(value, 1),
            ExplosiveBlockComponent::getMaxFragments)
        .add()
        .append(new KeyedCodec<>("ImpulseStrength", Codec.FLOAT, false),
            (component, value) -> component.impulseStrength = nonNegative(value, 0.0f),
            ExplosiveBlockComponent::getImpulseStrength)
        .add()
        .append(new KeyedCodec<>("VerticalLift", Codec.FLOAT, false),
            (component, value) -> component.verticalLift = nonNegative(value, 0.0f),
            ExplosiveBlockComponent::getVerticalLift)
        .add()
        .build();

    @Nullable
    private static ComponentType<EntityStore, ExplosiveBlockComponent> componentType;

    @Nonnull
    private String blockType = ExplosiveBlockPolicy.DEFAULT_BLOCK_TYPE;
    private int generation;
    private int maxGeneration;
    private int radius = 3;
    private int maxFragments = 32;
    private float impulseStrength = 12.0f;
    private float verticalLift = 0.35f;

    public ExplosiveBlockComponent() {
    }

    public ExplosiveBlockComponent(@Nullable String blockType,
        int generation,
        int maxGeneration,
        int radius,
        int maxFragments,
        float impulseStrength,
        float verticalLift) {
        this.blockType = sanitizeBlockType(blockType);
        this.generation = Math.max(0, generation);
        this.maxGeneration = Math.max(0, maxGeneration);
        this.radius = Math.max(1, radius);
        this.maxFragments = Math.max(1, maxFragments);
        this.impulseStrength = Math.max(0.0f, impulseStrength);
        this.verticalLift = Math.max(0.0f, verticalLift);
    }

    @Nonnull
    public String getBlockType() {
        return blockType;
    }

    public int getGeneration() {
        return generation;
    }

    public int getMaxGeneration() {
        return maxGeneration;
    }

    public int getRadius() {
        return radius;
    }

    public int getMaxFragments() {
        return maxFragments;
    }

    public float getImpulseStrength() {
        return impulseStrength;
    }

    public float getVerticalLift() {
        return verticalLift;
    }

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, ExplosiveBlockComponent> type) {
        componentType = Objects.requireNonNull(type, "type");
    }

    public static boolean isComponentTypeRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, ExplosiveBlockComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("Explosive block component is not registered");
        }
        return componentType;
    }

    @Nonnull
    @Override
    public ExplosiveBlockComponent clone() {
        return new ExplosiveBlockComponent(blockType,
            generation,
            maxGeneration,
            radius,
            maxFragments,
            impulseStrength,
            verticalLift);
    }

    @Nonnull
    private static String sanitizeBlockType(@Nullable String value) {
        return value == null || value.isBlank()
            ? ExplosiveBlockPolicy.DEFAULT_BLOCK_TYPE
            : value.trim();
    }

    private static int nonNegative(@Nullable Integer value, int defaultValue) {
        return Math.max(0, value != null ? value : defaultValue);
    }

    private static int atLeast(@Nullable Integer value, int minimum) {
        return Math.max(minimum, value != null ? value : minimum);
    }

    private static float nonNegative(@Nullable Float value, float defaultValue) {
        return Math.max(0.0f, value != null ? value : defaultValue);
    }
}
