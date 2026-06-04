package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.fallingblocks.FallingBlockImpact;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.fallingblocks.FallingBlockSettings;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.lang.reflect.Field;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class ImpulseExplosiveFallingBlockImpact extends FallingBlockImpact {

    public static final String CODEC_ID = "ImpulseExplode";
    public static final BuilderCodec<ImpulseExplosiveFallingBlockImpact> CODEC =
        BuilderCodec.builder(ImpulseExplosiveFallingBlockImpact.class,
                ImpulseExplosiveFallingBlockImpact::new)
            .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
                (impact, value) -> impact.spaceIdValue = positiveSpaceId(value),
                ImpulseExplosiveFallingBlockImpact::spaceIdValue)
            .add()
            .append(new KeyedCodec<>("BlockType", Codec.STRING, false),
                (impact, value) -> impact.settings = impact.settingsWithBlockType(value),
                impact -> impact.settings.getBlockType())
            .add()
            .append(new KeyedCodec<>("Generation", Codec.INTEGER, false),
                (impact, value) -> impact.settings = impact.settingsWithGeneration(value),
                impact -> impact.settings.getGeneration())
            .add()
            .append(new KeyedCodec<>("MaxGeneration", Codec.INTEGER, false),
                (impact, value) -> impact.settings = impact.settingsWithMaxGeneration(value),
                impact -> impact.settings.getMaxGeneration())
            .add()
            .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
                (impact, value) -> impact.settings = impact.settingsWithRadius(value),
                impact -> impact.settings.getRadius())
            .add()
            .append(new KeyedCodec<>("MaxFragments", Codec.INTEGER, false),
                (impact, value) -> impact.settings = impact.settingsWithMaxFragments(value),
                impact -> impact.settings.getMaxFragments())
            .add()
            .append(new KeyedCodec<>("ImpulseStrength", Codec.FLOAT, false),
                (impact, value) -> impact.settings = impact.settingsWithImpulseStrength(value),
                impact -> impact.settings.getImpulseStrength())
            .add()
            .append(new KeyedCodec<>("VerticalLift", Codec.FLOAT, false),
                (impact, value) -> impact.settings = impact.settingsWithVerticalLift(value),
                impact -> impact.settings.getVerticalLift())
            .add()
            .build();

    private static final Field FALLING_BLOCK_IMPACT_FIELD = impactField();

    private int spaceIdValue = 1;
    @Nonnull
    private ExplosiveBlockComponent settings = new ExplosiveBlockComponent();

    public ImpulseExplosiveFallingBlockImpact() {
    }

    public ImpulseExplosiveFallingBlockImpact(@Nonnull SpaceId spaceId,
        @Nonnull ExplosiveBlockComponent settings) {
        this.spaceIdValue = spaceId.value();
        this.settings = Objects.requireNonNull(settings, "settings").clone();
    }

    @Nonnull
    public static FallingBlockSettings fallingBlockSettings(@Nonnull SpaceId spaceId,
        @Nonnull ExplosiveBlockComponent settings) {
        FallingBlockSettings fallingBlockSettings = new RuntimeFallingBlockSettings();
        setImpact(fallingBlockSettings, new ImpulseExplosiveFallingBlockImpact(spaceId, settings));
        return fallingBlockSettings;
    }

    @Nonnull
    SpaceId spaceId() {
        return new SpaceId(spaceIdValue);
    }

    @Nonnull
    ExplosiveBlockComponent settings() {
        return settings.clone();
    }

    @Override
    public void apply(@Nonnull WorldChunk chunk,
        @Nonnull World world,
        @Nonnull BlockType blockType,
        @Nonnull Vector3d position,
        @Nonnull RotationTuple rotation,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        TimeResource time = store.getResource(TimeResource.getResourceType());
        ExplosiveBlockRuntime.explode(store,
            world,
            time,
            resource,
            spaceId(),
            new Vector3d(position),
            settings);
    }

    private int spaceIdValue() {
        return spaceIdValue;
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithBlockType(String blockType) {
        return new ExplosiveBlockComponent(blockType,
            settings.getGeneration(),
            settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithGeneration(Integer generation) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            generation != null ? generation : settings.getGeneration(),
            settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithMaxGeneration(Integer maxGeneration) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            settings.getGeneration(),
            maxGeneration != null ? maxGeneration : settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithRadius(Integer radius) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            settings.getGeneration(),
            settings.getMaxGeneration(),
            radius != null ? radius : settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithMaxFragments(Integer maxFragments) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            settings.getGeneration(),
            settings.getMaxGeneration(),
            settings.getRadius(),
            maxFragments != null ? maxFragments : settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithImpulseStrength(Float impulseStrength) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            settings.getGeneration(),
            settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            impulseStrength != null ? impulseStrength : settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    @Nonnull
    private ExplosiveBlockComponent settingsWithVerticalLift(Float verticalLift) {
        return new ExplosiveBlockComponent(settings.getBlockType(),
            settings.getGeneration(),
            settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            verticalLift != null ? verticalLift : settings.getVerticalLift());
    }

    private static int positiveSpaceId(Integer value) {
        return value != null && value > 0 ? value : 1;
    }

    private static void setImpact(@Nonnull FallingBlockSettings settings,
        @Nonnull FallingBlockImpact impact) {
        try {
            FALLING_BLOCK_IMPACT_FIELD.set(settings, impact);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to attach Impulse falling-block impact", exception);
        }
    }

    @Nonnull
    private static Field impactField() {
        try {
            Field field = FallingBlockSettings.class.getDeclaredField("impact");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class RuntimeFallingBlockSettings extends FallingBlockSettings {
    }
}
