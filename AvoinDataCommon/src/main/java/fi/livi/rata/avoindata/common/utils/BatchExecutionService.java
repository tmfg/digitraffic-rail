package fi.livi.rata.avoindata.common.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

@Service
public class BatchExecutionService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public static final int BATCH_SIZE = 200;

    public <ID_TYPE extends Comparable<ID_TYPE>, ENTITY_TYPE> List<ENTITY_TYPE> mapAndSort(
            final Function<List<ID_TYPE>, List<ENTITY_TYPE>> entityProvider,
            final List<ID_TYPE> ids,
            final Comparator<ENTITY_TYPE> order) {
        final List<Future<List<ENTITY_TYPE>>> streamFutures = new ArrayList<>();

        final List<ID_TYPE> uniqueIds = ImmutableSet.copyOf(ids).stream().sorted().toList();

        for (final List<ID_TYPE> subIds : Lists.partition(uniqueIds, BATCH_SIZE)) {
            final Future<List<ENTITY_TYPE>> streamFuture = executor.submit(() -> entityProvider.apply(subIds));
            streamFutures.add(streamFuture);
        }

        final List<ENTITY_TYPE> entities = new ArrayList<>(ids.size());
        for (final Future<List<ENTITY_TYPE>> streamFuture : streamFutures) {
            try {
                entities.addAll(streamFuture.get());
            } catch (final Exception e) {
                log.error("Error fetching entities", e);
            }
        }

        if (order != null) {
            entities.sort(order);
        }

        return entities;
    }

    public <I> void consume(final List<I> originalCollection, final Consumer<List<I>> batchFunction) {
        final List<List<I>> partitions = Lists.partition(originalCollection, 1000);
        for (final List<I> partition : partitions) {
            batchFunction.accept(partition);
        }
    }
}
