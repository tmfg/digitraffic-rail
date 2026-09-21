package fi.livi.rata.avoindata.updater.service.gtfs;

/**
 * What {@link TrakediaRouteService} knows about its own work, in its own vocabulary. The caller's
 * fallback policy is deliberately absent: only this layer can tell an empty {@code geometria} from
 * a route the shortest-path search could not connect.
 */
public interface RouteMetricsSink {

    /** Used when no GTFS run is bound, so outcomes are dropped rather than failing the caller. */
    RouteMetricsSink DISCARD = outcome -> {
    };

    void recordRouteOutcome(RouteOutcome outcome);

    enum RouteOutcome {
        RESOLVED,
        EMPTY_GEOMETRY,
        NO_PATH
    }
}
