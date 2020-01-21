package fi.livi.rata.avoindata.common.domain.trackwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.common.converter.StringListConverter;

import javax.persistence.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class TrackWorkPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    public Long partIndex;
    public LocalDate startDay;
    public Duration permissionMinimumDuration;
    public Boolean containsFireWork;
    public LocalTime plannedWorkingGap;

    @Convert(converter = StringListConverter.class)
    public List<String> advanceNotifications;

    @JoinColumns({
            @JoinColumn(name = "track_work_notification_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "track_work_notification_version", referencedColumnName = "version", nullable = false)
    })
    @ManyToOne(optional = false)
    @JsonIgnore
    public TrackWorkNotification trackWorkNotification;

    @OneToMany(mappedBy = "trackWorkPart", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<RumaLocation> locations = new HashSet<>();
}
