package org.neo4j.index.memory.provider;

import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.collection.primitive.PrimitiveLongSet;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.kernel.api.direct.BoundedIterable;
import org.neo4j.kernel.api.exceptions.index.IndexCapacityExceededException;
import org.neo4j.kernel.api.index.*;
import org.neo4j.kernel.impl.api.index.IndexUpdateMode;
import org.neo4j.kernel.impl.api.index.sampling.NonUniqueIndexSampler;
import org.neo4j.kernel.impl.api.index.sampling.UniqueIndexSampler;
import org.neo4j.register.Register;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MemoryIndex implements IndexAccessor, IndexPopulator, IndexUpdater {

    private final SortedMap<Object, long[]> indexData;
    private final Set<Class> valueTypesInIndex = new HashSet<>();

    private InternalIndexState state = InternalIndexState.POPULATING;
    private NonUniqueIndexSampler nonUniqueIndexSampler = new NonUniqueIndexSampler(1000);
    private UniqueIndexSampler uniqueIndexSampler = new UniqueIndexSampler();
    private long maxCount = 0;
    private String failure;

    public MemoryIndex(final SortedMap<Object, long[]> map){
        this.indexData = map;
    }

    @Override
    public void create() throws IOException {
        clear();
    }

    private void clear() {
        this.indexData.clear();
        maxCount = 0;
    }

    public InternalIndexState getState() {
        return this.state;
    }

    @Override
    public void add(final long nodeId, final Object propertyValue) throws IndexEntryConflictException, IOException, IndexCapacityExceededException {
        long[] nodes = this.indexData.get(propertyValue);
        if (nodes==null || nodes.length==0) {
            sample(propertyValue);
            this.indexData.put(propertyValue, new long[]{nodeId});
            maxCount++;
            return;
        }
        final int idx=this.indexOf(nodes,nodeId);
        if (idx!=-1) return;
        sample(propertyValue);
        nodes = Arrays.copyOfRange(nodes, 0, nodes.length + 1);
        nodes[nodes.length-1]=nodeId;
        maxCount++;
        this.indexData.put(propertyValue, nodes);
    }

    private void sample(Object propertyValue) {
        nonUniqueIndexSampler.include(propertyValue.toString());
        valueTypesInIndex.add(propertyValue.getClass());
        uniqueIndexSampler.increment(1);
    }

    @Override
    public void verifyDeferredConstraints(PropertyAccessor propertyAccessor)  {
//        System.out.println("verifyDeferredConstraints" +propertyAccessor);
    }

    @Override
    public IndexUpdater newPopulatingUpdater(PropertyAccessor propertyAccessor) throws IOException {
        return this;
    }

    @Override
    public void close(boolean populationCompletedSuccessfully) throws IOException, IndexCapacityExceededException {
        if (populationCompletedSuccessfully) {
            this.state = InternalIndexState.ONLINE;
            this.failure = null;
        } else {
            this.state = InternalIndexState.FAILED;
        }
    }

    @Override
    public void markAsFailed(String s) throws IOException {
        this.failure = s;
    }

    @Override
    public long sampleResult(Register.DoubleLong.Out out) {
        return uniqueIndexSampler.result(out);
    }

    @Override
    public Reservation validate(Iterable<NodePropertyUpdate> iterable) throws IOException, IndexCapacityExceededException {
        return Reservation.EMPTY;
    }

    @Override
    public void process(final NodePropertyUpdate update) throws IOException, IndexEntryConflictException, IndexCapacityExceededException {
        switch (update.getUpdateMode()) {
            case ADDED:
                this.add(update.getNodeId(), update.getValueAfter());
                break;
            case CHANGED:
                this.removed(update.getNodeId(), update.getValueBefore());
                this.add(update.getNodeId(), update.getValueAfter());
                break;
            case REMOVED:
                this.removed(update.getNodeId(), update.getValueBefore());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public void remove(PrimitiveLongSet nodeIds) throws IOException {
        final Iterator<Map.Entry<Object,long[]>> entries = this.indexData.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<Object, long[]> entry = entries.next();
            long[] nodes = entry.getValue();
            int existingCount = nodes.length;

            final PrimitiveLongIterator nodeIdIter = nodeIds.iterator();

            while(nodeIdIter.hasNext()) {

                final long nodeId = nodeIdIter.next();

                final int idx = this.indexOf(nodes, nodeId);
                if (idx != -1) {
                    if (existingCount == 1) {
                        entries.remove();
                        break;
                    } else {
                        System.arraycopy(nodes,idx,nodes,idx-1, existingCount-idx-1);
                        nodes = Arrays.copyOfRange(nodes, 0, existingCount - 1);
                        entry.setValue(nodes);
                        existingCount--;
                    }
                }
            }
        }
    }

    private void removed(final long nodeId, final Object propertyValue) {
        long[] nodes = this.indexData.get(propertyValue);
        if (nodes==null || nodes.length ==0) return;
        final int idx=this.indexOf(nodes,nodeId);
        if (idx==-1) return;
        final int existingCount = nodes.length;
        nonUniqueIndexSampler.exclude(propertyValue.toString());
        if (existingCount == 1) {
            maxCount--;
            this.indexData.remove(propertyValue);
            return;
        }
        System.arraycopy(nodes,idx,nodes,idx-1, existingCount-idx-1);
        nodes = Arrays.copyOfRange(nodes, 0, existingCount - 1);
        maxCount--;
        this.indexData.put(propertyValue, nodes);
    }

    private int indexOf(final long[] nodes, final long nodeId) {
//        if (nodes.length > 256) return Arrays.binarySearch(nodes,nodeId);
        int end = nodes.length - 1;
        for (int i = 0; i != end; i++) {
            if (nodes[i]==nodeId) return i;
        }
        return -1;
    }

    @Override
    public IndexUpdater newUpdater(IndexUpdateMode mode) {
        return this;
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public IndexReader newReader() {
        return new MemoryIndexReader(this.indexData,nonUniqueIndexSampler,valueTypesInIndex);
    }

    @Override
    public void drop() throws IOException {
        clear();
    }

    @Override
    public void force() throws IOException {
    }

    @Override
    public void close() throws IOException {
        clear();
    }

    // todo snapshot
    @Override
    public BoundedIterable<Long> newAllEntriesReader() {
        return new BoundedIterable<Long>() {
            final long max = maxCount;
            public long maxCount() { return max; }

            public void close() throws Exception { }

            @Override
            public Iterator<Long> iterator() {
                // todo snapshot
                Iterator<long[]> values = indexData.values().iterator();
                CombiningPrimitiveLongIterator it = new CombiningPrimitiveLongIterator(values);
                return new Iterator<Long>() {
                    public boolean hasNext() { return it.hasNext(); }
                    public Long next() { return it.next(); }
                };
            }
        };
    }

    @Override
    public ResourceIterator<File> snapshotFiles() throws IOException {
        return (ResourceIterator<File>) ResourceIterator.EMPTY;
    }

    public void shutdown() {
        clear();
    }

    public String getFailure() {
        return failure;
    }
}
