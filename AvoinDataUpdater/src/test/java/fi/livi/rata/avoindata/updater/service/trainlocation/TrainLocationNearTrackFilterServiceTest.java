package fi.livi.rata.avoindata.updater.service.trainlocation;


import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainLocationFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TrainLocationNearTrackFilterServiceTest extends BaseTest {

    //TODO mock ObjectMapper infra-api call with raiteet.geojson
    @Autowired
    private TrainLocationNearTrackFilterService trainLocationNearTrackFilterService;

    @Autowired
    private TrainLocationFactory factory;

    @Test
    public void helsinkiShouldMatch() {
        Assert.assertEquals(true, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(385754, 6672611)));
    }

    @Test
    public void seaSouthOfHelsinkiShouldNotMatch() {
        Assert.assertEquals(false, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(386167, 6666698)));
    }

    @Test
    public void laplandWildernessShouldNotMatch() {
        Assert.assertEquals(false, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(473333, 7589740)));
    }

    @Test
    public void northernMostTrainTrackShouldMatch() {
        Assert.assertEquals(true, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(364214, 7475031)));
    }

    @Test
    public void tampereShouldMatch() {
        Assert.assertEquals(true, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(327785, 6823456)));
    }

    @Test
    public void meters500NorthOfTampereShouldNotMatch() {
        //663m because rectangle is inclined
        Assert.assertEquals(false, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(327785, 6823456 + 663)));
    }

    @Test
    public void privateTrackShouldMatch() {
        //Uusikaupunki factory
        Assert.assertEquals(true, trainLocationNearTrackFilterService.isTrainLocationNearTrack(factory.create(192063,6752583)));
    }
}