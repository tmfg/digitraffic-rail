package fi.livi.rata.avoindata.updater.service.gtfs;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMetricsSink;

/**
 * The metrics accumulator of the GTFS run executing on this thread. Bound for the duration of a run.
 * <p>
 * Anything that influences behaviour rather than merely observing it is passed explicitly instead.
 */
public final class GtfsRunScope {
    private static final ScopedValue<GtfsRunMetrics> METRICS = ScopedValue.newInstance();

    private GtfsRunScope() {
    }

    public static void run(final GtfsRunMetrics metrics, final Runnable body) {
        ScopedValue.where(METRICS, metrics).run(body);
    }

    public static <T, X extends Throwable> T call(final GtfsRunMetrics metrics,
                                                  final ScopedValue.CallableOp<? extends T, X> body) throws X {
        return ScopedValue.where(METRICS, metrics).call(body);
    }

    /**
     * Each layer sees only the recordings it owns. Unbound access throws
     * {@link java.util.NoSuchElementException}, so a dropped binding fails loudly instead of
     * silently discarding counters.
     */
    public static ShapeMetricsSink shapeMetrics() {
        return METRICS.get();
    }

    public static FeedMetricsSink feedMetrics() {
        return METRICS.get();
    }

    /** The shared WebClient also serves non-GTFS callers, so an unbound scope is normal here. */
    public static InfraApiMetricsSink infraApiMetrics() {
        return METRICS.isBound() ? METRICS.get() : InfraApiMetricsSink.DISCARD;
    }

    /** The route cache is warmed outside a run by tests and dev endpoints, so absence is normal. */
    public static RouteMetricsSink routeMetrics() {
        return METRICS.isBound() ? METRICS.get() : RouteMetricsSink.DISCARD;
    }
}
