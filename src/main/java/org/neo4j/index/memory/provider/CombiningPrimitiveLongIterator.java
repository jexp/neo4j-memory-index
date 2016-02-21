package org.neo4j.index.memory.provider;

import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.collection.primitive.PrimitiveLongIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author mh
 * @since 20.02.16
 */
public class CombiningPrimitiveLongIterator implements PrimitiveLongIterator {
    private final Iterator<long[]> it;
    PrimitiveLongIterator current;

    public CombiningPrimitiveLongIterator(Iterator<long[]> it) {
        this.it = it;
        current = nextIt();
    }

    private PrimitiveLongIterator nextIt() {
        long[] data;
        while (it.hasNext()) {
            data = it.next();
            if (data != null && data.length > 0) return PrimitiveLongCollections.iterator(data);
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        return current != null && current.hasNext();
    }

    @Override
    public long next() {
        if (current.hasNext()) {
            long value = current.next();
            if (!current.hasNext()) current = nextIt();
            return value;
        }
        throw new NoSuchElementException("Combining-PrimitiveLongIterator called with hasNext() = false");
    }
}
