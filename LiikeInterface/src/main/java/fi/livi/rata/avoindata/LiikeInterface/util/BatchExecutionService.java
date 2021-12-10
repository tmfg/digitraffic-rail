package fi.livi.rata.avoindata.LiikeInterface.util;

import com.google.common.collect.Lists;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class BatchExecutionService {
    public <I, O> List<O> execute(List<I> originalCollection, Function<List<I>, List<O>> batchFunction) {
        return execute(originalCollection, batchFunction, 1000);
    }

    public <I, O> List<O> execute(List<I> originalCollection, Function<List<I>, List<O>> batchFunction,
            Integer numberOfElementsPerBatch) {
        List<O> outputList = new ArrayList<>();

        final List<List<I>> partitions = Lists.partition(originalCollection, numberOfElementsPerBatch);
        for (final List<I> partition : partitions) {
            outputList.addAll(batchFunction.apply(partition));
        }

        return outputList;
    }
}
