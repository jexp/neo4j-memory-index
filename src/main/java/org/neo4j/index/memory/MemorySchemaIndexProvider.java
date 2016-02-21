package org.neo4j.index.memory;

import org.neo4j.index.memory.provider.MemoryIndex;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.*;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.kernel.impl.store.SchemaStore;
import org.neo4j.kernel.impl.store.StoreFactory;
import org.neo4j.kernel.impl.storemigration.SchemaIndexMigrator;
import org.neo4j.kernel.impl.storemigration.StoreMigrationParticipant;
import org.neo4j.kernel.impl.storemigration.UpgradableDatabase;
import org.neo4j.kernel.impl.util.CopyOnWriteHashMap;
import org.neo4j.kernel.monitoring.Monitors;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import static org.neo4j.index.memory.MemoryIndexProviderFactory.PROVIDER_DESCRIPTOR;

public class MemorySchemaIndexProvider extends SchemaIndexProvider {
    static int PRIORITY;
    static {
        PRIORITY = 2;
    }

    private final Map<Long, MemoryIndex> indexes = new CopyOnWriteHashMap<>();

    public MemorySchemaIndexProvider(final Config config){
        super(PROVIDER_DESCRIPTOR, PRIORITY);
    }

    @Override
    public StoreMigrationParticipant storeMigrationParticipant(FileSystemAbstraction fs, PageCache pageCache) {
        return new NoopStoreMigrationParticipant();
    }

    @Override
    public IndexPopulator getPopulator(long indexId, IndexDescriptor indexDescriptor, IndexConfiguration indexConfiguration, IndexSamplingConfig indexSamplingConfig) {
        final MemoryIndex index = new MemoryIndex(createIndex());
        this.indexes.put(indexId, index);
        return index;
    }

    private TreeMap<Object, long[]> createIndex() {
        return new TreeMap<>();
    }

    @Override
    public IndexAccessor getOnlineAccessor(final long indexId, IndexConfiguration indexConfiguration, IndexSamplingConfig indexSamplingConfig) throws IOException {
        final MemoryIndex index = this.indexes.get(indexId);
        if (index == null || index.getState() != InternalIndexState.ONLINE)
            throw new IllegalStateException("Index " + indexId + " not online yet");
        return index;
    }

    @Override
    public String getPopulationFailure(long indexId) throws IllegalStateException {
        MemoryIndex index = indexes.get(indexId);
        return index != null ? index.getFailure() : null;
    }

    @Override
    public InternalIndexState getInitialState(final long indexId) {
        final MemoryIndex index = this.indexes.get(indexId);
        return index != null ? index.getState() : InternalIndexState.POPULATING;
    }

    @Override
    public void shutdown() throws Throwable {
        for (MemoryIndex memoryIndex : indexes.values()) {
            memoryIndex.shutdown();
        }
        super.shutdown();
    }

}
