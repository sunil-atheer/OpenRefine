
package org.openrefine.model;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;

import org.openrefine.io.IOUtils;
import org.openrefine.model.changes.ChangeData;
import org.openrefine.model.changes.ChangeDataSerializer;
import org.openrefine.model.changes.IndexedData;
import org.openrefine.process.ProgressReporter;

/**
 * Stores change data in a rowid-indexed RDD.
 * 
 * @author Antonin Delpeuch
 *
 * @param <T>
 */
public class SparkChangeData<T> implements ChangeData<T> {

    private final JavaPairRDD<Long, T> data;
    private final SparkDatamodelRunner runner;

    /**
     * Constructs a change data.
     * 
     * The RDD is expected not to contain any null value (they should be filtered out first).
     * 
     * @param data
     * @param runner
     */
    public SparkChangeData(JavaPairRDD<Long, T> data, SparkDatamodelRunner runner) {
        this.data = data;
        this.runner = runner;
    }

    public JavaPairRDD<Long, T> getData() {
        return data;
    }

    @Override
    public Iterator<IndexedData<T>> iterator() {
        return data.map(tuple -> new IndexedData<T>(tuple._1, tuple._2)).toLocalIterator();
    }

    @Override
    public T get(long rowId) {
        List<T> rows = data.lookup(rowId);
        if (rows.size() == 0) {
            return null;
        } else if (rows.size() > 1) {
            throw new IllegalStateException(String.format("Found %d change data elements at index %d", rows.size(), rowId));
        } else {
            return rows.get(0);
        }
    }

    @Override
    public DatamodelRunner getDatamodelRunner() {
        return runner;
    }

    @Override
    public void saveToFile(File file, ChangeDataSerializer<T> serializer) throws IOException {
        IOUtils.deleteDirectoryIfExists(file);
        data
                .map(r -> serializeIndexedData(serializer, r))
                .saveAsTextFile(file.getAbsolutePath(), GzipCodec.class);
    }

    @Override
    public void saveToFile(File file, ChangeDataSerializer<T> serializer, ProgressReporter progressReporter) throws IOException {
        saveToFile(file, serializer);
        // TODO more granular progress reporting? this requires knowing the expected size of the RDD,
        // which should probably be passed when constructing the object (so that it can be inferred from
        // the parent Grid)
        progressReporter.reportProgress(100);
    }

    protected static <T> String serializeIndexedData(ChangeDataSerializer<T> serializer, Tuple2<Long, T> data) throws IOException {
        String serialized = (new IndexedData<T>(data._1, data._2)).writeAsString(serializer);
        return serialized;
    }

}
