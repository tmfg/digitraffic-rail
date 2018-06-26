package fi.livi.rata.avoindata.updater.service;

import org.osgeo.proj4j.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class Wgs84ConversionService {
    public static final int NUMBER_OF_DECIMALS = 6;
    private CoordinateTransform transformer;
    private CoordinateTransform reverseTransformer;

    @PostConstruct
    private void setup() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem coordinateTransformFrom = crsFactory.createFromParameters("EPSG:3067",
                "+proj=utm +zone=35 ellps=GRS80 +units=m +no_defs");
        CoordinateReferenceSystem coordinateTransformTo = crsFactory.createFromParameters("EPSG:4326",
                "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs");
        CoordinateTransformFactory coordinateTransformFactory = new CoordinateTransformFactory();
        transformer = coordinateTransformFactory.createTransform(coordinateTransformFrom, coordinateTransformTo);
        reverseTransformer = coordinateTransformFactory.createTransform(coordinateTransformTo, coordinateTransformFrom);
    }

    public ProjCoordinate liviToWgs84(double iKoordinaatti, double pKoordinaatti) {
        ProjCoordinate from = new ProjCoordinate();
        ProjCoordinate to = new ProjCoordinate();
        from.x = iKoordinaatti;
        from.y = pKoordinaatti;

        transformer.transform(from, to);
        to.setValue(round(to.x,NUMBER_OF_DECIMALS),round(to.y, NUMBER_OF_DECIMALS));
        return to;
    }

    public ProjCoordinate wgs84Tolivi(double x, double y) {
        ProjCoordinate from = new ProjCoordinate();
        ProjCoordinate to = new ProjCoordinate();
        from.x = x;
        from.y = y;

        reverseTransformer.transform(from, to);
        return to;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
