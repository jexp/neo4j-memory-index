package org.neo4j.index.memory.provider;

import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.kernel.api.exceptions.index.IndexNotFoundKernelException;
import org.neo4j.kernel.api.index.IndexReader;
import org.neo4j.kernel.impl.api.index.sampling.NonUniqueIndexSampler;
import org.neo4j.register.Register;

import java.util.*;

public class MemoryIndexReader implements IndexReader {

    private static final long[] EMPTY_LONGS = new long[0];
    private SortedMap<Object, long[]> snapshot;
    private final NonUniqueIndexSampler nonUniqueIndexSampler;
    private final Set<Class> valueTypesInIndex;

    MemoryIndexReader(final SortedMap<Object, long[]> snapshot, NonUniqueIndexSampler nonUniqueIndexSampler, Set<Class> valueTypesInIndex) {
        this.snapshot = snapshot;
        this.nonUniqueIndexSampler = nonUniqueIndexSampler;
        this.valueTypesInIndex = valueTypesInIndex;
    }

    @Override
    public PrimitiveLongIterator seek(Object value) {
        final long[] result = snapshot.get(value);
        return PrimitiveLongCollections.iterator(result == null || result.length == 0 ? EMPTY_LONGS : result);
    }

    @Override
    public PrimitiveLongIterator rangeSeekByNumberInclusive(Number lower, Number upper) {
        return new CombiningPrimitiveLongIterator(snapshot.subMap(lower, upper).values().iterator());
    }

    @Override
    public PrimitiveLongIterator rangeSeekByString(String lower, boolean includeLower, String upper, boolean includeUpper) {
        if (includeUpper) upper = successorString(upper);
        if (!includeLower) lower = lower + (char)0;
        return new CombiningPrimitiveLongIterator(snapshot.subMap(lower, upper).values().iterator());
    }

    @Override
    public PrimitiveLongIterator rangeSeekByPrefix(String prefix) {
        return new CombiningPrimitiveLongIterator(snapshot.subMap(prefix, successorString(prefix)).values().iterator());
    }

    private String successorString(String str) {
        StringBuilder sb = new StringBuilder(str);
        int last = sb.length() - 1;
        sb.setCharAt(last,(char)(sb.charAt(last)+1));
        return sb.toString();
    }

    @Override
    public PrimitiveLongIterator scan() {
        Iterator<long[]> it = snapshot.values().iterator();
        return new CombiningPrimitiveLongIterator(it);
    }

    // TODO why nodeId ???
    @Override
    public int countIndexedNodes(long nodeId, Object propertyValue) {
        final long[] result = snapshot.get(propertyValue);
        return result == null ? 0 : result.length;
    }

    @Override
    public Set<Class> valueTypesInIndex() {
        return valueTypesInIndex;
    }

    @Override
    public long sampleIndex(Register.DoubleLong.Out out) throws IndexNotFoundKernelException {
        return nonUniqueIndexSampler.result(out);
    }

    @Override
    public void close() {}

}
