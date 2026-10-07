package fi.livi.rata.avoindata.updater.service.siri.vm;

/**
 * Per-cycle counters for the {@code rail.siri.generation} wide event: how many live locations were read and how
 * many resolved to a published journey and were emitted as a {@code VehicleActivity}.
 */
public record SiriVmStats(long locationsReceived, long activitiesEmitted) {

    public static SiriVmStats empty() {
        return new SiriVmStats(0, 0);
    }
}
