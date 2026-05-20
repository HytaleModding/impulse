package dev.hytalemodding.impulse.core.voxel;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class ChunkSectionAccess {

    private ChunkSectionAccess() {
    }

    @Nullable
    static BlockSection blockSection(@Nonnull World world, int chunkX, int sectionY, int chunkZ) {
        if (sectionY < 0 || sectionY > ChunkUtil.indexSection(ChunkUtil.HEIGHT_MINUS_1)) {
            return null;
        }

        Ref<ChunkStore> chunkRef = world.getChunkStore()
            .getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }

        Store<ChunkStore> store = world.getChunkStore().getStore();
        // FIXME: ChunkColumn is deprecated! removal in the future 3d chunks update!
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) {
            return null;
        }

        Holder<ChunkStore>[] holders = column.getSectionHolders();
        if (holders != null && sectionY < holders.length) {
            Holder<ChunkStore> holder = holders[sectionY];
            if (holder != null) {
                return holder.ensureAndGetComponent(BlockSection.getComponentType());
            }
        }

        Ref<ChunkStore> sectionRef = column.getSection(sectionY);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return store.getComponent(sectionRef, BlockSection.getComponentType());
    }
}
