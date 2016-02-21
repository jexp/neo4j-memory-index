package org.neo4j.index.memory;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.IteratorUtil;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author mh
 * @since 20.02.16
 */
public class LifecycleTest {

    public static final Label LABEL = DynamicLabel.label("Foo");
    public static final String KEY = "bar";

    public static void main(String[] args) {
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase("target/test.db");
        try (Transaction tx = db.beginTx()) {
            int count = IteratorUtil.count(db.findNodes(LABEL));
            System.out.println("count = " + count);
            tx.success();
        }
        try (Transaction tx = db.beginTx()) {
            db.schema().indexFor(LABEL).on(KEY).create();
            tx.success();
        } catch( Exception e) {
            System.err.println(e.getMessage());
        }
        try (Transaction tx = db.beginTx()) {
            db.schema().awaitIndexesOnline(10, TimeUnit.SECONDS);
        }
        try (Transaction tx = db.beginTx()) {
            db.createNode(LABEL).setProperty(KEY, 42);
            tx.success();
        }
        db.shutdown();

        db = new GraphDatabaseFactory().newEmbeddedDatabase("target/test.db");
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> nodes = db.findNodes(LABEL, KEY, 42);
            int count = IteratorUtil.count(nodes);
            assertEquals(true, count > 0);
        }
        db.shutdown();
    }
}
