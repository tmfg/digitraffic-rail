package fi.livi.rata.avoindata.common.domain.trackwork;

import fi.livi.rata.avoindata.common.converter.StringListConverter;

import javax.persistence.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class TrackWorkPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long partIndex;
    public LocalDate startDay;
    public Duration permissionMinimumDuration;
    public Boolean containsFireWork;
    public LocalTime plannedWorkingGap;

    @Convert(converter = StringListConverter.class)
    public List<String> advanceNotifications;

    @JoinColumn(name = "track_work_notification_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    public TrackWorkNotification trackWorkNotification;

    @OneToMany(mappedBy = "trackWorkPart", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.PERSIST })
    public Set<RumaLocation> locations = new HashSet<>();
}
