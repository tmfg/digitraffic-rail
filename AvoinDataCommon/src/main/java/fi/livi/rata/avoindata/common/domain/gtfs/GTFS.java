package fi.livi.rata.avoindata.common.domain.gtfs;

import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
public class GTFS {
    @Lob
    @Column(length=1024*1024*100)
    public byte[] data;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    @CreatedDate
    public ZonedDateTime created;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String fileName;
}
