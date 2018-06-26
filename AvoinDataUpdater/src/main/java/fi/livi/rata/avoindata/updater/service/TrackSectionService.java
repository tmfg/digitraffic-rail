package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrackRangeRepository;
import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrackSectionRepository;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TrackSectionService {
    @Autowired
    private TrackSectionRepository trackSectionRepository;

    @Autowired
    private TrackRangeRepository trackRangeRepository;

    @Transactional
    public void updateTrackSections(final TrackSection[] trackSections) {
        trackRangeRepository.deleteAllInBatch();
        trackSectionRepository.deleteAllInBatch();

        List<TrackRange> ranges = new ArrayList<>();
        for (final TrackSection trackSection : trackSections) {
            ranges.addAll(trackSection.ranges);
        }
        trackSectionRepository.persist(Arrays.asList(trackSections));

        trackRangeRepository.persist(ranges);
    }
}
