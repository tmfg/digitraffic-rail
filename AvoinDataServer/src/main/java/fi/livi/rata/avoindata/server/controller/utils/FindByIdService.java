package fi.livi.rata.avoindata.server.controller.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Service
public class FindByIdService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private ExecutorService executor = Executors.newFixedThreadPool(10);

    public static final int ENTITY_FETCH_SIZE = 100;

    public <ID_TYPE extends Serializable, ENTITY_TYPE> List<ENTITY_TYPE> findById(
            final Function<List<ID_TYPE>, List<ENTITY_TYPE>> entityProvider,
            final List<ID_TYPE> ids,
            final Comparator<ENTITY_TYPE> order) {
        final List<Future<List<ENTITY_TYPE>>> streamFutures = new ArrayList<>();

        final ArrayList<ID_TYPE> uniqueIds = Lists.newArrayList(Sets.newLinkedHashSet(ids));
        for (final List<ID_TYPE> subIds : Lists.partition(uniqueIds, ENTITY_FETCH_SIZE)) {
            final Future<List<ENTITY_TYPE>> streamFuture = executor.submit(() -> {
                final List<ENTITY_TYPE> entities = entityProvider.apply(subIds);

                return entities;
            });
            streamFutures.add(streamFuture);
        }

        final List<ENTITY_TYPE> entities = new ArrayList<>();
        for (final Future<List<ENTITY_TYPE>> streamFuture : streamFutures) {
            try {
                entities.addAll(streamFuture.get());
            } catch (Exception e) {
                log.error("Error fetching entities", e);
            }
        }

        if (order != null) {
            Collections.sort(entities, order);
        }

        return entities;
    }
}
