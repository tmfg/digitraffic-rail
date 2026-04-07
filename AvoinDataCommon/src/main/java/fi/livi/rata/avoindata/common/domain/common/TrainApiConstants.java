package fi.livi.rata.avoindata.common.domain.common;

public final class TrainApiConstants {

    /**
     * Maximum number of trains that may share the same API version number.
     * Updater uses this to split trains into version chunks. Server
     * uses it to limit the number of trains returned per versioned query.
     */
    public static final int MAX_TRAINS_PER_VERSION = 2500;

    private TrainApiConstants() {
    }
}

