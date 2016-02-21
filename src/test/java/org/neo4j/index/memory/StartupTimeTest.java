package org.neo4j.index.memory;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author mh
 * @since 20.02.16
 */
public class StartupTimeTest {

    public static final String PATH = "target/perf-load.db";
    public static final Label LABEL = DynamicLabel.label("Foo");
    public static final int BATCH = 100_000;
    public static final String KEY = "bar";
    public static final int RUNS = 1000;

    public static void main(String[] args) {
        boolean exists = new File(PATH).exists();
        if (!exists) createData();
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(PATH);
        createIndex(db);
        long time = waitIndexOnline(db);
        System.out.println("Startup took "+time+" seconds");
        db.shutdown();
    }

    private static long waitIndexOnline(GraphDatabaseService db) {
        long start = System.currentTimeMillis();
        try (Transaction tx = db.beginTx()) {
            db.schema().awaitIndexesOnline(10, TimeUnit.MINUTES);
            tx.success();
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }
        long end = System.currentTimeMillis();
        return (end - start) / 1000;
    }

    private static void createIndex(GraphDatabaseService db) {
        try (Transaction tx = db.beginTx()) {
            db.schema().indexFor(LABEL).on(KEY).create();
            tx.success();
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void createData() {
        long start = System.currentTimeMillis();
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(PATH);
        int count=0;
        for (int j = 0; j < RUNS; j++) {
            try (Transaction tx = db.beginTx()) {
                for (int i = 0; i < BATCH; i++) {
                    db.createNode(LABEL).setProperty(KEY, j * BATCH + i);
                    count++;
                }
                tx.success();
                System.out.println("Created "+ BATCH +" nodes");
            }
        }
        db.shutdown();
        long end = System.currentTimeMillis();
        System.out.println("Creating "+count+" nodes took "+((end-start)/1000)+" seconds");
    }
}
