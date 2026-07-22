package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Default PetiStopSource returning an empty list.
 * This is the Pass-2 bean — NeTEx generation works with zero PETI data.
 * Pass 3 replaces this with a real HTTP-backed source.
 */
@Component
public class EmptyPetiStopSource implements PetiStopSource {

    @Override
    public List<PetiStop> getStops() {
        return List.of();
    }
}
