package fi.livi.rata.avoindata.common.domain.gtfs;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Immutable
@Table(name = "train")
public class GTFSTrain {
    @EmbeddedId
    public TrainId id;

    @Column
    public boolean cancelled;

    @Column
    public Long version;

    @Column
    public long trainCategoryId;

    @Column
    public long trainTypeId;

    @OneToMany(mappedBy = "train", fetch = FetchType.EAGER)
    public List<GTFSTimeTableRow> timeTableRows = new ArrayList<>();
}
