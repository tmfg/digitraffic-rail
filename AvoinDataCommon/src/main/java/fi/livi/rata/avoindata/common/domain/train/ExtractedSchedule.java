package fi.livi.rata.avoindata.common.domain.train;

import java.time.ZonedDateTime;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

@Entity
public class ExtractedSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String capacityId;
    @Embedded
    public TrainId trainId;
    public Long version;
    public ZonedDateTime timestamp;
    public Long scheduleId;

    @Override
    public String toString() {
        return "ExtractedSchedule{" +
                "scheduleId=" + scheduleId +
                ", capacityId='" + capacityId + '\'' +
                ", trainId=" + trainId +
                ", version=" + version +
                '}';
    }
}
