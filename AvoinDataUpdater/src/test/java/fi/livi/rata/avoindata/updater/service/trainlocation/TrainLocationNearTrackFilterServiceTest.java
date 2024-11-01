package fi.livi.rata.avoindata.updater.service.trainlocation;


import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainLocationFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

public class TrainLocationNearTrackFilterServiceTest extends BaseTest {

    //TODO mock ObjectMapper infra-api call with raiteet.geojson
    @Autowired
    private TrainLocationNearTrackFilterService trainLocationNearTrackFilterService;

    @Autowired
    private TrainLocationFactory factory;

    @ParameterizedTest
    @MethodSource("getCoordinates")
    public void testCoordinates(final String name, final int x, final int y, final boolean shouldMatch) {
        final TrainLocation location = factory.create(x, y);

        if(shouldMatch) {
            Assertions.assertTrue(trainLocationNearTrackFilterService.isTrainLocationNearTrack(location), name + " should match");
        } else {
            Assertions.assertFalse(trainLocationNearTrackFilterService.isTrainLocationNearTrack(location), name + " should not match");
        }
    }

    static Stream<Arguments> getCoordinates() {
        return Stream.of(
                Arguments.arguments("Helsinki", 385754, 6672611, true),
                Arguments.arguments("South of Helsinki", 386167, 6666698, false),
                Arguments.arguments("Lapland wilderness", 473333, 7589740, false),
                Arguments.arguments("Northest track", 364214, 7475031, true),
                Arguments.arguments("Tampere", 327785, 6823456, true),
                Arguments.arguments("500m north of Tampere", 327785, 6823456 + 663, false),
                Arguments.arguments("Uusikaipunki private track", 192063,6752583, true)
                );
    }
}