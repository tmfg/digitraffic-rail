package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrackSectionRepository;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
public class TrackSectionController extends AMetadataController {

    @Autowired
    private TrackSectionRepository trackSectionRepository;

    @ApiOperation("Returns list of track sections")
    @RequestMapping(value = "track-sections", method = RequestMethod.GET)
    public List<TrackSection> getTrackSections(HttpServletResponse response) {
        final List<TrackSection> items = trackSectionRepository.findAllWithJoins();
        setCache(response, items);
        return items;
    }
}
