package fi.livi.rata.avoindata.LiikeInterface.services;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.util.BatchExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class ClassifiedTrainFilter {
    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Autowired
    private BatchExecutionService bes;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public <E> Iterable<E> filterClassifiedTrains(Iterable<E> items, Function<E, JunapaivaPrimaryKey> keyProvider) {
        final HashMultimap<JunapaivaPrimaryKey, E> byTrainMap = HashMultimap.create();

        for (final E item : items) {
            final JunapaivaPrimaryKey key = keyProvider.apply(item);
            byTrainMap.put(key, item);
        }

        final List<JunapaivaPrimaryKey> classifiedTrains = findClassifiedTrains(byTrainMap.keySet());

        List<E> output = new ArrayList<>();
        for (final E item : items) {
            final JunapaivaPrimaryKey key = keyProvider.apply(item);
            if (!classifiedTrains.contains(key)) {
                output.add(item);
            } else {
                log.info("Filtered {} (entity: {}) as classified", key, item);
            }
        }

        return output;
    }

    private List<JunapaivaPrimaryKey> findClassifiedTrains(final Iterable<JunapaivaPrimaryKey> keys) {
        final List<JunapaivaPrimaryKey> classifiedTrains = bes.execute(Lists.newArrayList(keys),
                k -> Lists.newArrayList(junapaivaRepository.findClassifiedTrains(k)));

        return classifiedTrains;
    }
}
