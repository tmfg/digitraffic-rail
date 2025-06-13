package fi.livi.rata.avoindata.common.domain.gtfs;

public final class StopIdGenerator {
    private StopIdGenerator() {
    }

    public static String createStopId(final String stationCode, final String commercialTrack) {
        if(stationCode == null) {
            return null;
        }

        // if commercialTrack is set, then create stopId as SHORTCODE_COMMERCIALTRACK
        // otherwise use SHORTCODE_0
        if(commercialTrack == null || commercialTrack.equals("")) {
            return String.format("%s_0", stationCode);
        }

        return String.format("%s_%s", stationCode, commercialTrack);
    }

}
