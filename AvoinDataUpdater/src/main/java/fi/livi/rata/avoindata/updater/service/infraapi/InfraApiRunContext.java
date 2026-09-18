package fi.livi.rata.avoindata.updater.service.infraapi;

/**
 * Binds a metrics sink to the thread driving a batch job, so the shared WebClient can attribute
 * requests without every call site passing a collector down.
 * <p>
 * The sink is read at subscribe time, which for the updater's blocking calls is the caller thread.
 */
public final class InfraApiRunContext {
    private static final ThreadLocal<InfraApiMetricsSink> CURRENT = new ThreadLocal<>();

    private InfraApiRunContext() {
    }

    public static void bind(final InfraApiMetricsSink sink) {
        CURRENT.set(sink);
    }

    public static void unbind() {
        CURRENT.remove();
    }

    public static InfraApiMetricsSink current() {
        return CURRENT.get();
    }
}
