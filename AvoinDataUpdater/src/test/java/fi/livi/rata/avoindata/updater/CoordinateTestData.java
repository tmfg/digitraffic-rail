package fi.livi.rata.avoindata.updater;

import com.vividsolutions.jts.geom.Coordinate;

public final class CoordinateTestData {

    public static final double TAMPERE_WGS84_X = 23.774862;
    public static final double TAMPERE_WGS84_Y = 61.486365;
    public static final double TAMPERE_TM35FIN_X = 328288.5;
    public static final double TAMPERE_TM35FIN_Y = 6821211;
    public static final Coordinate TAMPERE_COORDINATE_TM35FIN = new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y);
    public static final Coordinate TAMPERE_COORDINATE_TM35FIN_DEVIATED = new Coordinate(TAMPERE_TM35FIN_X + 1, TAMPERE_TM35FIN_Y + 2);

    public static final double VUOSAARI_WGS84_X = 25.167955;
    public static final double VUOSAARI_WGS84_Y = 60.227507;
    public static final double VUOSAARI_TM35FIN_X = 398524;
    public static final double VUOSAARI_TM35FIN_Y = 6678157;
    public static final Coordinate VUOSAARI_COORDINATE_TM35FIN = new Coordinate(VUOSAARI_TM35FIN_X, VUOSAARI_TM35FIN_Y);
    public static final Coordinate VUOSAARI_COORDINATE_TM35FIN_DEVIATED = new Coordinate(VUOSAARI_TM35FIN_X + 1, VUOSAARI_TM35FIN_Y + 1);

    public static final double NAANTALI_WGS84_X = 22.040037;
    public static final double NAANTALI_WGS84_Y = 60.469929;
    public static final double NAANTALI_TM35FIN_X = 227453;
    public static final double NAANTALI_TM35FIN_Y = 6714022;

}
