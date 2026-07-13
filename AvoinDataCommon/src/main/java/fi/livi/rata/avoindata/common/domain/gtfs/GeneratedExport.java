package fi.livi.rata.avoindata.common.domain.gtfs;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "gtfs")
public class GeneratedExport {
    @Lob
    @Column(length = 1024 * 1024 * 100)
    public byte[] data;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @CreatedDate
    public ZonedDateTime created;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String fileName;
}
