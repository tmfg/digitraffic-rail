package fi.livi.rata.avoindata.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class BatchExecutionService {
    public static final int BATCH_SIZE = 1000;

    public <I> void consume(List<I> originalCollection, Consumer<List<I>> batchFunction) {
        final List<List<I>> partitions = Lists.partition(originalCollection, BATCH_SIZE);
        for (final List<I> partition : partitions) {
            batchFunction.accept(partition);
        }
    }

    public <I, O> List<O> transform(List<I> originalCollection, Function<List<I>, List<O>> batchFunction) {
        List<O> outputList = new ArrayList<>(originalCollection.size());

        final List<List<I>> partitions = Lists.partition(originalCollection, BATCH_SIZE);
        for (final List<I> partition : partitions) {
            outputList.addAll(batchFunction.apply(partition));
        }

        return outputList;
    }
}