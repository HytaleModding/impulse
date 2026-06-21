package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

/**
 * BSON holder file storage for row-native PhysicsStore persistence.
 */
public final class PhysicsStoreHolderStorage {

    public static final int SCHEMA_VERSION = 1;
    private static final String DIRECTORY = "physicsstore";
    private static final String FILE_NAME = "holders.bson";
    private static final String SCHEMA_VERSION_FIELD = "SchemaVersion";
    private static final String HOLDERS_FIELD = "Holders";

    private PhysicsStoreHolderStorage() {
    }

    @Nonnull
    public static CompletableFuture<Void> save(@Nonnull PhysicsStore physicsStore) {
        return save(physicsStore.getStore());
    }

    @Nonnull
    public static CompletableFuture<Void> save(@Nonnull Store<PhysicsStore> store) {
        List<byte[]> holderBlobs = PhysicsStoreHolderPersistence.capturePersistentHolderBlobs(
            store);
        byte[] document = BsonUtil.writeToBytes(document(holderBlobs));
        Path file = file(store.getExternalData());
        return CompletableFuture.runAsync(() -> writeBinaryAtomic(file, document));
    }

    @Nonnull
    public static LoadResult load(@Nonnull Store<PhysicsStore> store) {
        Path file = file(store.getExternalData());
        if (!Files.exists(file)) {
            return LoadResult.missing();
        }

        BsonDocument document;
        try {
            document = BsonUtil.readFromBytes(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read PhysicsStore holder storage: " + file,
                exception);
        }
        if (document == null) {
            throw new IllegalStateException("PhysicsStore holder storage is empty: " + file);
        }
        int schemaVersion = document.getInt32(SCHEMA_VERSION_FIELD, new BsonInt32(0)).getValue();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported PhysicsStore holder storage schema "
                + schemaVersion + "; expected " + SCHEMA_VERSION);
        }

        BsonArray holders = document.getArray(HOLDERS_FIELD, new BsonArray());
        List<Holder<PhysicsStore>> decodedHolders = new ArrayList<>(holders.size());
        for (BsonValue value : holders) {
            decodedHolders.add(PhysicsStoreHolderPersistence.decodeHolder(
                store.getRegistry(),
                value.asBinary().getData()));
        }
        PhysicsStoreHolderPreflight.Result preflight = PhysicsStoreHolderPreflight.validate(
            decodedHolders);
        if (!preflight.valid()) {
            throw new IllegalStateException(String.join("; ", preflight.errors()));
        }

        int loaded = 0;
        for (Holder<PhysicsStore> holder : decodedHolders) {
            store.addEntity(holder, AddReason.LOAD);
            loaded++;
        }
        return new LoadResult(true, loaded);
    }

    @Nonnull
    public static Summary summary(@Nonnull Store<PhysicsStore> store) {
        Path file = file(store.getExternalData());
        if (!Files.exists(file)) {
            return Summary.missing();
        }
        BsonDocument document;
        try {
            document = BsonUtil.readFromBytes(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read PhysicsStore holder storage: " + file,
                exception);
        }
        if (document == null) {
            return Summary.missing();
        }
        int schemaVersion = document.getInt32(SCHEMA_VERSION_FIELD, new BsonInt32(0)).getValue();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported PhysicsStore holder storage schema "
                + schemaVersion + "; expected " + SCHEMA_VERSION);
        }
        BsonArray holders = document.getArray(HOLDERS_FIELD, new BsonArray());
        int spaces = 0;
        int bodies = 0;
        int joints = 0;
        for (BsonValue value : holders) {
            Holder<PhysicsStore> holder = PhysicsStoreHolderPersistence.decodeHolder(
                store.getRegistry(),
                value.asBinary().getData());
            if (holder.getComponent(SpaceComponent.getComponentType()) != null) {
                spaces++;
            }
            if (holder.getComponent(BodyComponent.getComponentType()) != null) {
                bodies++;
            }
            if (holder.getComponent(JointComponent.getComponentType()) != null) {
                joints++;
            }
        }
        return new Summary(true, spaces, bodies, joints);
    }

    @Nonnull
    static Path file(@Nonnull PhysicsStore physicsStore) {
        Path savePath = physicsStore.getWorld().getSavePath();
        return primaryFile(savePath);
    }

    @Nonnull
    private static Path primaryFile(@Nonnull Path savePath) {
        return savePath.resolve(DIRECTORY).resolve(FILE_NAME);
    }

    @Nonnull
    private static BsonDocument document(@Nonnull List<byte[]> holderBlobs) {
        BsonArray holders = new BsonArray();
        for (byte[] holderBlob : holderBlobs) {
            holders.add(new BsonBinary(holderBlob));
        }
        return new BsonDocument()
            .append(SCHEMA_VERSION_FIELD, new BsonInt32(SCHEMA_VERSION))
            .append(HOLDERS_FIELD, holders);
    }

    private static void writeBinaryAtomic(@Nonnull Path file, @Nonnull byte[] bytes) {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            try {
                Files.move(temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write PhysicsStore holder storage: " + file,
                exception);
        }
    }

    public record LoadResult(boolean present, int loadedCount) {

        @Nonnull
        private static LoadResult missing() {
            return new LoadResult(false, 0);
        }
    }

    public record Summary(boolean present, int spaces, int bodies, int joints) {

        @Nonnull
        private static Summary missing() {
            return new Summary(false, 0, 0, 0);
        }
    }
}
