package fi.livi.rata.avoindata.updater.service.trainlocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.utils.PreviousAndNext;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import org.osgeo.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Service
public class TrackBoundingBoxesService {
    public static final int DISTANCE_FROM_LINE = 500;
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private BoundingBoxService boundingBoxService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${infra-api.url}")
    private String infraApiUrl;

    @Cacheable("trackBoundingBoxes")
    public RTree<TrainBoundary, Geometry> getBoundingBoxes() {
        try {
            RTree<TrainBoundary, Geometry> tree = RTree.star().maxChildren(6).create();
            tree = createInfraApiTracks(tree);
            tree = createPrivateTracks(tree);

            return tree;
        } catch (Exception e) {
            log.error("Error forming Tracks", e);
            return RTree.star().maxChildren(6).create();
        }
    }

    private RTree<TrainBoundary, Geometry> createPrivateTracks(RTree<TrainBoundary, Geometry> tree) throws IOException {
        //Sappo Lohja
        tree = createPrivateTrack(tree,
                "[[23.956160545349118,60.1714032807602],[23.95667552947998,60.172918839697616],[23.955988883972168," +
                        "60.17424222834386],[23.949809074401855,60.17866025267855],[23.950796127319336,60.18572358818014],[" +
                        "23.946032524108887,60.18994805963756],[23.944787979125977,60.19412932430069],[23.939809799194336," +
                        "60.19587847087935]]");

        //Heinola
        tree = createPrivateTrack(tree,
                "[[26.043992042541504,61.201340646861205],[26.047253608703613,61.20148536274234],[26.06231689453125," +
                        "61.19770185760271],[26.066265106201172,61.18841690577185],[26.068110466003418,61.1875068746406]," +
                        "[26.076779365539547,61.18603835989958],[26.078495979309082,61.18436292853285],[26.079096794128418," +
                        "61.18223231309773],[26.08330249786377,61.17674997065357],[26.083216667175293,61.173232494249206]]");

        //Valkeakoski
        tree = createPrivateTrack(tree,
                "[[24.025039672851562,61.266044923124916],[24.019289016723633,61.26723119825499],[24.015190601348873," +
                        "61.27001618989007],[24.014976024627686,61.27111980344012]]");


        //Äänekoski Metso
        tree = createPrivateTrack(tree,
                "[[25.733714103698727,62.60264353565156],[25.736117362976074,62.59974045624514],[25.736417770385742," +
                        "62.59703460973859],[25.74620246887207,62.59148389041853],[25.75671672821045,62.59185923865363]," +
                        "[25.7658576965332,62.588895834050014]]");

        //Tahkoluoto
        tree = createPrivateTrack(tree,
                "[[21.382570266723633,61.63005986625746],[21.3869047164917,61.62987633979817],[21.389822959899902," +
                        "61.63107943787513],[21.390509605407715,61.63503505768449],[21.39291286468506,61.63656415659073]," +
                        "[21.397075653076172,61.636666093830314],[21.407289505004883,61.63558554199828],[21.438188552856445," +
                        "61.63005986625746]]");

        //Varkaus
        tree = createPrivateTrack(tree,
                "[[27.945013046264645,62.27253537531779],[27.935185432434082,62.27618912708058],[27.933340072631832," +
                        "62.27724723469522],[27.933254241943356,62.27868460434227],[27.935056686401364,62.28004205711511]," +
                        "[27.93810367584228,62.280501181701545],[27.942395210266113,62.280920376295995],[27.944326400756836," +
                        "62.28503216619843]]");

        //Uusikaupunki (Hanko)
        tree = createPrivateTrack(tree,
                "[[21.339354515075684,60.788924138772465],[21.338045597076416,60.789824714096156],[21.33765935897827," +
                        "60.79133259748825],[21.340041160583496,60.794096866031936],[21.347508430480957,60.79679807981309]," +
                        "[21.35289430618286,60.79730060607904]]");

        //Kuopio (Laukanranta)
        tree = createPrivateTrack(tree,
                "[[27.7324104309082,63.0975256652119],[27.74423360824585,63.09499150001593],[27.749619483947754,63.09571002139669]]");

        //Kaskinen (Kalasatama)
        tree = createPrivateTrack(tree,
                "[[21.24833106994629,62.37391680215925],[21.24833106994629,62.363865708875494],[21.243739128112793,62.35880903428286]," +
                        "[21.240391731262207,62.357654241029735],[21.237645149230957,62.357634330411486],[21.226186752319336," +
                        "62.35964523613726],[21.224513053894043,62.360302235532],[21.221938133239746,62.36436336699908]," +
                        "[21.22082233428955,62.36193471719653],[21.221165657043457,62.35588214427196],[21.21734619140625," +
                        "62.348713044626706]]");
        return tree;
    }

    private RTree<TrainBoundary, Geometry> createInfraApiTracks(RTree<TrainBoundary, Geometry> tree) throws IOException {
        log.info("Fetching tracks from {}", infraApiUrl);
        JsonNode trackNodes = objectMapper.readTree(new URL(infraApiUrl));

        for (final JsonNode featureNode : trackNodes.get("features")) {
            final JsonNode geometryNode = featureNode.get("geometry");
            if (isMultiLineNode(geometryNode)) {
                for (final JsonNode coordinates : geometryNode.get("coordinates")) {
                    for (final PreviousAndNext<JsonNode> pan : PreviousAndNext.build(Lists.newArrayList(coordinates.iterator()))) {
                        if (pan.next != null) {
                            final Point startingPoint = nodeToPoint(pan.current);
                            final Point endingPoint = nodeToPoint(pan.next);
                            tree = createAndAddBoundingBoxToTree(tree, startingPoint, endingPoint);
                        }
                    }
                }
            }
        }
        return tree;
    }

    private RTree<TrainBoundary, Geometry> createPrivateTrack(RTree<TrainBoundary, Geometry> tree,
            final String trackJson) throws IOException {
        final List<List<Double>> track = Arrays.asList(objectMapper.readValue(trackJson, List.class)).get(0);

        for (final PreviousAndNext<List<Double>> listPreviousAndNext : PreviousAndNext.build(Lists.newArrayList(track.iterator()))) {

            final List<Double> current = listPreviousAndNext.current;
            final List<Double> next = listPreviousAndNext.next;

            if (next != null) {
                final ProjCoordinate startingProjCoordinate = wgs84ConversionService.wgs84Tolivi(current.get(0), current.get(1));
                Point startingPoint = new Point(startingProjCoordinate.x, startingProjCoordinate.y);

                final ProjCoordinate endingProjCoordinate = wgs84ConversionService.wgs84Tolivi(next.get(0), next.get(1));
                Point endingPoint = new Point(endingProjCoordinate.x, endingProjCoordinate.y);

                tree = createAndAddBoundingBoxToTree(tree, startingPoint, endingPoint);
            }
        }
        return tree;
    }

    private RTree<TrainBoundary, Geometry> createAndAddBoundingBoxToTree(RTree<TrainBoundary, Geometry> tree, final Point startingPoint,
            final Point endingPoint) {
        final List<Point> boundingBox = boundingBoxService.createBoundingBox(startingPoint, endingPoint, DISTANCE_FROM_LINE);

        final Rectangle minMaxRectangle = createMinMaxRectangle(boundingBox);

        tree = tree.add(new TrainBoundary(boundingBox), minMaxRectangle);
        return tree;
    }

    private Rectangle createMinMaxRectangle(final List<Point> boundingBox) {
        Double minX = Double.MAX_VALUE;
        Double minY = Double.MAX_VALUE;
        Double maxX = Double.MIN_VALUE;
        Double maxY = Double.MIN_VALUE;

        for (final Point box : boundingBox) {
            if (box.getX() < minX) {
                minX = box.getX();
            }
            if (box.getX() > maxX) {
                maxX = box.getX();
            }
            if (box.getY() < minY) {
                minY = box.getY();
            }
            if (box.getY() > maxY) {
                maxY = box.getY();
            }
        }

        return Geometries.rectangle(minX, minY, maxX, maxY);
    }

    private boolean isMultiLineNode(final JsonNode geometryNode) {
        return geometryNode != null && geometryNode.get("type") != null && geometryNode.get("type").textValue().equals("MultiLineString");
    }

    private Point nodeToPoint(JsonNode pointNode) {
        return new Point(pointNode.get(0).doubleValue(), pointNode.get(1).doubleValue());
    }
}
