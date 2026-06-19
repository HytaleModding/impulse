package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/**
 * Compatibility reader for legacy DTO resource files.
 */
public final class PersistentPhysicsStoreStorage {

    private static final String RESOURCE_DIRECTORY = "resources";
    private static final String FILE_NAME = "PersistentPhysicsStore.json";

    private PersistentPhysicsStoreStorage() {
    }

    @Nonnull
    public static LoadResult load(@Nonnull Store<PhysicsStore> store) {
        Path file = fileOrNull(store.getExternalData());
        if (file == null) {
            return LoadResult.missing();
        }
        BsonDocument document;
        try {
            document = BsonUtil.readDocument(file).join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Could not read legacy PhysicsStore DTO storage: "
                + file, exception.getCause() != null ? exception.getCause() : exception);
        }
        if (document == null) {
            return LoadResult.missing();
        }
        PersistentPhysicsStoreResource resource = PersistentPhysicsStoreResource.CODEC.decode(
            document,
            new ExtraInfo());
        if (resource == null) {
            throw new IllegalStateException("Legacy PhysicsStore DTO storage decoded to null: "
                + file);
        }
        return new LoadResult(true, resource);
    }

    @Nonnull
    static Path file(@Nonnull PhysicsStore physicsStore) {
        return physicsStore.getWorld().getSavePath().resolve(RESOURCE_DIRECTORY).resolve(FILE_NAME);
    }

    @Nullable
    private static Path fileOrNull(@Nonnull PhysicsStore physicsStore) {
        Path savePath = physicsStore.getWorld().getSavePath();
        return savePath != null ? savePath.resolve(RESOURCE_DIRECTORY).resolve(FILE_NAME) : null;
    }

    public record LoadResult(boolean present,
                             @Nonnull PersistentPhysicsStoreResource resource) {

        @Nonnull
        private static LoadResult missing() {
            return new LoadResult(false, new PersistentPhysicsStoreResource());
        }
    }
}
