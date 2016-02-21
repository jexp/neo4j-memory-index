package org.neo4j.index.memory;

import org.junit.Before;

import java.io.IOException;

public class LuceneIndexTest extends BasicIndexTest {
    @Override
    @Before
    public void setUp() throws IOException {
        MemorySchemaIndexProvider.PRIORITY = 0;
        super.setUp();
    }
}

